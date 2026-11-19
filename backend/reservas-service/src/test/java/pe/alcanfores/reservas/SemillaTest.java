package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.reservas.config.ReservasPropiedades;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.repositorio.CancelacionRepositorio;
import pe.alcanfores.reservas.repositorio.ClienteRepositorio;
import pe.alcanfores.reservas.repositorio.HistorialRepositorio;
import pe.alcanfores.reservas.semilla.SemillaDatos;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La semilla crea una demo coherente (y solo si la tabla de reservas está vacía). */
class SemillaTest extends PruebaIntegracion {

    @Autowired
    private ClienteRepositorio clientes;
    @Autowired
    private CancelacionRepositorio cancelaciones;
    @Autowired
    private HistorialRepositorio historial;
    @Autowired
    private TransactionTemplate transacciones;

    @Test
    void siembraClientesReservasPasadasYFuturasConCuatroPorValidar() throws Exception {
        SemillaDatos semilla = new SemillaDatos(new ReservasPropiedades(false, new ReservasPropiedades.Semilla(true), null, null, null),
                reservas, turnos, clientes, cancelaciones, historial, turnoServicio, complejo, transacciones);
        semilla.run();

        assertThat(clientes.count()).isEqualTo(25);
        List<Reserva> todas = reservas.findAll();
        assertThat(todas).hasSizeBetween(130, 140);
        assertThat(todas).allMatch(Reserva::isEsSemilla);
        assertThat(reservas.countByEstadoAndEstadoPago(EstadoReserva.PENDIENTE, EstadoPago.EN_REVISION)).isEqualTo(4);
        assertThat(reservas.countByEstadoAndEstadoPago(EstadoReserva.PENDIENTE, EstadoPago.OBSERVADO)).isEqualTo(2);
        assertThat(todas).anyMatch(r -> r.getEstado() == EstadoReserva.CANCELADA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE asistencia = 'NO_ASISTIO'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT origen) FROM reserva", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cancelacion WHERE cancelado_por = 'SISTEMA'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva r JOIN turno t ON t.id_turno = r.id_turno "
                + "WHERE t.franja = 'NOCHE'", Integer.class)).isGreaterThan(todas.size() / 2);

        mvc.perform(get("/interno/reservas/para-semilla").header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(todas.size()))
                .andExpect(jsonPath("$[0].codigoReserva").exists())
                .andExpect(jsonPath("$[0].cliente.dni").exists())
                .andExpect(jsonPath("$[0].montoTotal").exists());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.porValidar").value(4));
        mvc.perform(get("/api/admin/reportes/reservas").header("Authorization", token("ADMIN"))
                        .param("desde", HOY.minusDays(45).toString()).param("hasta", HOY.plusDays(7).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.registradas").value(todas.size()));

        // Un segundo arranque no vuelve a sembrar.
        semilla.run();
        assertThat(reservas.count()).isEqualTo(todas.size());
    }
}
