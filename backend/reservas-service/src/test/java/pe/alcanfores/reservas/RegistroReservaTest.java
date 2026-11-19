package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ClienteRepositorio;

import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/reservas, validaciones del §3, disponibilidad y consulta del cliente. */
class RegistroReservaTest extends PruebaIntegracion {

    @Autowired
    private ClienteRepositorio clientes;

    @Test
    void registraLaReservaPendienteConPlazoAdelantoYDatosNormalizados() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 19);
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(t.getId(), "70123321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoReserva", startsWith("ALC-")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoPago").value("SIN_PAGO"))
                .andExpect(jsonPath("$.venceEn").value("2026-11-20T11:00:00"))
                .andExpect(jsonPath("$.cancha.nombre").value("Cancha 1 · Fútbol 5"))
                .andExpect(jsonPath("$.fecha").value("2026-11-21"))
                .andExpect(jsonPath("$.horaInicio").value("19:00"))
                .andExpect(jsonPath("$.horaFin").value("20:00"))
                .andExpect(jsonPath("$.montoTotal").value(80.00))
                .andExpect(jsonPath("$.montoAdelanto").value(40.00))
                .andExpect(jsonPath("$.cliente.nombres").value("Diego Armando"))
                .andExpect(jsonPath("$.cliente.apellidos").value("Ramos Quispe"))
                .andExpect(jsonPath("$.cliente.dniEnmascarado").value("7****321"))
                .andExpect(jsonPath("$.cliente.telefono").value("987654321"))
                .andExpect(jsonPath("$.cliente.correo").value("diego.ramos@example.com"));

        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        ArgumentCaptor<ReservaRegistradaEvento> evento = ArgumentCaptor.forClass(ReservaRegistradaEvento.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_REGISTRADA), evento.capture());
        assertThat(evento.getValue().origen()).isEqualTo("WEB");
        assertThat(evento.getValue().metodoPagoPrevisto()).isEqualTo("YAPE");
        assertThat(evento.getValue().reserva().cliente().correo()).isEqualTo("diego.ramos@example.com");
        assertThat(evento.getValue().reserva().montoAdelanto()).isEqualByComparingTo("40.00");
    }

    @Test
    void elMismoDniActualizaCelularYCorreoSinDuplicarAlCliente() throws Exception {
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321");
        String otro = cuerpoReserva(turno(2, HOY.plusDays(1), 20).getId(), "70123321")
                .replace("+51 987 654 321", "912 345 678").replace("Diego.Ramos@Example.com", "nuevo@example.com");
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(otro)).andExpect(status().isCreated());
        assertThat(clientes.count()).isEqualTo(1);
        Cliente c = clientes.findByDni("70123321").orElseThrow();
        assertThat(c.getTelefono()).isEqualTo("912345678");
        assertThat(c.getCorreo()).isEqualTo("nuevo@example.com");
    }

    @Test
    void validaCadaCampoConLosMensajesDelSpec() throws Exception {
        String malo = """
                {"idTurno": null, "cliente": {"dni": "1234", "nombres": "J", "apellidos": "P3rez",
                 "telefono": "812345678", "correo": "no-es-correo"},
                 "metodoPago": "BITCOIN", "aceptaTerminos": false, "aceptaPrivacidad": null}
                """;
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("idTurno", "cliente.dni", "cliente.nombres",
                        "cliente.apellidos", "cliente.telefono", "cliente.correo", "metodoPago", "aceptaTerminos",
                        "aceptaPrivacidad")))
                .andExpect(jsonPath("$.errores[?(@.campo=='cliente.dni')].mensaje").value("El DNI debe tener 8 dígitos."))
                .andExpect(jsonPath("$.errores[?(@.campo=='cliente.nombres')].mensaje").value("Usa solo letras (2 a 60)."))
                .andExpect(jsonPath("$.errores[?(@.campo=='cliente.telefono')].mensaje")
                        .value("Ingresa un celular peruano de 9 dígitos que empiece con 9."))
                .andExpect(jsonPath("$.errores[?(@.campo=='cliente.correo')].mensaje")
                        .value("Ingresa un correo válido; ahí te enviamos la copia de tu reserva."))
                .andExpect(jsonPath("$.errores[?(@.campo=='metodoPago')].mensaje").value("Elige un método de pago."))
                .andExpect(jsonPath("$.errores[?(@.campo=='aceptaTerminos')].mensaje")
                        .value("Debes aceptar los términos y la política de privacidad."));
        assertThat(reservas.count()).isZero();
    }

    @Test
    void sinClienteNiCorreoDevuelveUnErrorPorCampo() throws Exception {
        String sinCliente = """
                {"idTurno": 5, "metodoPago": "PLIN", "aceptaTerminos": true, "aceptaPrivacidad": true}
                """;
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(sinCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cliente"))
                .andExpect(jsonPath("$.mensaje").value("Completa los datos del cliente."));
    }

    @Test
    void rechazaFechasFueraDeLosDiasVisibles() throws Exception {
        Turno lejano = turno(1, HOY.plusDays(20), 19);
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(lejano.getId(), "70123321")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                .andExpect(jsonPath("$.mensaje").value("Elige una fecha entre hoy y los próximos 14 días."));
    }

    @Test
    void rechazaUnTurnoQueEmpiezaEnMenosDeTreintaMinutos() throws Exception {
        Turno pronto = turno(1, HOY, 11);
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(pronto.getId(), "70123321")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("idTurno"))
                .andExpect(jsonPath("$.mensaje").value("Ese turno ya no está disponible."));
    }

    @Test
    void rechazaUnTurnoBloqueadoOInexistente() throws Exception {
        Turno t = turno(4, HOY.plusDays(1), 9);
        turnoServicio.bloquear(t.getId(), "Mantenimiento");
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(t.getId(), "70123321")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_NO_DISPONIBLE"));
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(999999, "70123321")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("idTurno"));
    }

    @Test
    void disponibilidadMarcaOcupadoPasadoYBloqueado() throws Exception {
        Turno ocupado = turno(1, HOY, 19);
        reservarWeb(ocupado.getId(), "70123321");
        turnoServicio.bloquear(turno(2, HOY, 15).getId(), "Evento privado");
        mvc.perform(get("/api/disponibilidad").param("fecha", HOY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-11-20"))
                .andExpect(jsonPath("$.canchas.length()").value(4))
                .andExpect(jsonPath("$.canchas[0].turnos.length()").value(15))
                .andExpect(jsonPath("$.canchas[0].turnos[0].horaInicio").value("08:00"))
                .andExpect(jsonPath("$.canchas[0].turnos[0].estado").value("PASADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[3].estado").value("PASADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[4].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.canchas[0].turnos[11].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[11].franja").value("NOCHE"))
                .andExpect(jsonPath("$.canchas[0].turnos[11].precio").value(80.00))
                .andExpect(jsonPath("$.canchas[1].turnos[7].estado").value("BLOQUEADO"));
        mvc.perform(get("/api/disponibilidad").param("fecha", HOY.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
        mvc.perform(get("/api/disponibilidad").param("fecha", HOY.toString()).param("idCancha", "77"))
                .andExpect(status().isNotFound());
    }

    @Test
    void calendarioCuentaTurnosLibresPorDia() throws Exception {
        turno(1, HOY, 19);
        turno(1, HOY.plusDays(1), 19);
        mvc.perform(get("/api/disponibilidad/calendario").param("desde", HOY.toString()).param("dias", "3").param("idCancha", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].total").value(15))
                .andExpect(jsonPath("$[0].libres").value(11))
                .andExpect(jsonPath("$[1].libres").value(15))
                .andExpect(jsonPath("$[2].total").value(0));
        mvc.perform(get("/api/disponibilidad/calendario").param("dias", "90")).andExpect(status().isBadRequest());
    }

    @Test
    void elClienteConsultaConCodigoYDniYVeLaLineaDeTiempo() throws Exception {
        JsonNode r = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321");
        String codigo = r.get("codigoReserva").asText();
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoReserva").value(codigo))
                .andExpect(jsonPath("$.saldo").value(80.00))
                .andExpect(jsonPath("$.montoPagado").value(0.00))
                .andExpect(jsonPath("$.asistencia").value("PENDIENTE"))
                .andExpect(jsonPath("$.linea[0].evento").value("REGISTRADA"))
                .andExpect(jsonPath("$.puedeCancelar").value(true))
                .andExpect(jsonPath("$.puedeReenviarComprobante").value(true))
                .andExpect(jsonPath("$.puedeResenar").value(false))
                .andExpect(jsonPath("$.cancelacion").doesNotExist());
    }

    @Test
    void codigoODniIncorrectosDevuelvenElMismo404() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        String mensaje = "No encontramos una reserva con ese código y DNI. Revisa ambos datos.";
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123322"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(get("/api/reservas/ALC-AAAAAA").param("dni", "70123321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(get("/api/reservas/ALC-0000").param("dni", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
