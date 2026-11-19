package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.reservas.dto.ReservaDtos.ClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ReservaServicio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF-11: un turno no se puede reservar dos veces, ni siquiera con solicitudes simultáneas. */
class DobleReservaIntegracionTest extends PruebaIntegracion {

    @Autowired
    private ReservaServicio servicio;

    @Test
    void dosHilosReservanElMismoTurnoALaVezYSoloUnoGana() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 19);
        int hilos = 2;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            String dni = "4000000" + i;
            Callable<String> tarea = () -> {
                largada.await();
                try {
                    return servicio.registrar(solicitud(t.getId(), dni)).codigoReserva();
                } catch (NegocioExcepcion e) {
                    return e.getStatus() + " " + e.getCodigo();
                }
            };
            resultados.add(pool.submit(tarea));
        }
        largada.countDown();
        List<String> salidas = new ArrayList<>();
        for (Future<String> f : resultados) {
            salidas.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(salidas).filteredOn(s -> s.startsWith("ALC-")).hasSize(1);
        assertThat(salidas).filteredOn(s -> s.equals("409 TURNO_OCUPADO")).hasSize(1);
        assertThat(reservas.count()).isEqualTo(1);
        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        verify(publicador, times(1)).publicar(eq(Eventos.RESERVA_REGISTRADA), any(ReservaRegistradaEvento.class));
    }

    @Test
    void cincoHilosSimultaneosDejanUnaSolaReservaVigente() throws Exception {
        Turno t = turno(2, HOY.plusDays(2), 20);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String dni = "4100000" + i;
            resultados.add(pool.submit(() -> {
                largada.await();
                try {
                    servicio.registrar(solicitud(t.getId(), dni));
                    return true;
                } catch (NegocioExcepcion e) {
                    assertThat(e.getStatus()).isEqualTo(409);
                    return false;
                }
            }));
        }
        largada.countDown();
        int ganadores = 0;
        for (Future<Boolean> f : resultados) {
            ganadores += f.get(60, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ganadores).isEqualTo(1);
        assertThat(reservas.count()).isEqualTo(1);
    }

    @Test
    void elSegundoPostDelMismoTurnoRecibe409ConElCuerpoEstandar() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 21);
        reservarWeb(t.getId(), "42000001");
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(t.getId(), "42000002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("TURNO_OCUPADO"))
                .andExpect(jsonPath("$.mensaje").value("Ese turno acaba de ser reservado por otra persona. Elige otro horario."))
                .andExpect(jsonPath("$.ruta").value("/api/reservas"));
    }

    @Test
    void laColumnaGeneradaTurnoActivoImpideDosReservasVigentesAunqueSeSalteElBloqueo() throws Exception {
        Turno t = turno(3, HOY.plusDays(1), 18);
        reservarWeb(t.getId(), "43000001");
        Long idCliente = jdbc.queryForObject("SELECT id_cliente FROM cliente LIMIT 1", Long.class);
        String insertar = "INSERT INTO reserva (codigo_reserva, id_cliente, id_turno, estado, estado_pago, origen, monto_total,"
                + " monto_adelanto, fecha_registro) VALUES (?, ?, ?, 'PENDIENTE', 'SIN_PAGO', 'WEB', 120, 60, NOW())";
        assertThatThrownBy(() -> jdbc.update(insertar, "ALC-ZZZZZZ", idCliente, t.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Una reserva CANCELADA del mismo turno sí se permite (no ocupa el turno).
        jdbc.update(insertar.replace("'PENDIENTE'", "'CANCELADA'"), "ALC-YYYYYY", idCliente, t.getId());
        assertThat(reservas.count()).isEqualTo(2);
    }

    private static RegistroReservaSolicitud solicitud(Long idTurno, String dni) {
        return new RegistroReservaSolicitud(idTurno, new ClienteSolicitud(dni, "Ana", "Pérez", "987654321", "ana@example.com"),
                "YAPE", true, true);
    }
}
