package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Turno;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/admin/reservas/{codigo}/reprogramar: ADMIN y RECEPCION cambian el horario con motivo, sin el plazo ni el límite
 * del cliente, pero con las reglas de estado y disponibilidad. El detalle trae el historial de cambios.
 */
class ReprogramacionPanelTest extends PruebaIntegracion {

    static final String DNI = "70123321";
    static final LocalDate MANANA = HOY.plusDays(1);

    private ResultActions reprogramarPanel(String rol, String codigo, String cuerpo) throws Exception {
        return mvc.perform(post("/api/admin/reservas/" + codigo + "/reprogramar").header("Authorization", token(rol))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(Long id, String motivo) {
        return "{\"idsTurno\": [" + id + "], \"motivo\": " + (motivo == null ? "null" : "\"" + motivo + "\"") + "}";
    }

    @Test
    void elPanelCambiaSinLimiteConMotivoYElDetalleMuestraElHistorial() throws Exception {
        Turno t19 = turno(1, MANANA, 19);
        Turno t20 = turno(1, MANANA, 20);
        Turno t21 = turno(2, MANANA, 21);
        String codigo = reservarWeb(t19.getId(), DNI).get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + codigo + "/reprogramar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\": \"" + DNI + "\", \"idsTurno\": [" + t20.getId() + "]}")).andExpect(status().isOk());

        // El cliente ya usó su cambio; el panel (RECEPCION) puede seguir cambiando.
        reprogramarPanel("RECEPCION", codigo, cuerpo(t21.getId(), "El cliente llamó para jugar más tarde"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancha.id").value(2))
                .andExpect(jsonPath("$.horaInicio").value("21:00"))
                .andExpect(jsonPath("$.montoTotal").value(90.0))
                .andExpect(jsonPath("$.puedeReprogramar").value(true))
                .andExpect(jsonPath("$.reprogramaciones.length()").value(2))
                .andExpect(jsonPath("$.reprogramaciones[0].actor").value("PANEL"))
                .andExpect(jsonPath("$.reprogramaciones[0].usuario").value("mtorres"))
                .andExpect(jsonPath("$.reprogramaciones[0].motivo").value("El cliente llamó para jugar más tarde"))
                .andExpect(jsonPath("$.reprogramaciones[0].anterior.cancha").value("Cancha 1 · Fútbol 5"))
                .andExpect(jsonPath("$.reprogramaciones[0].anterior.horaInicio").value("20:00"))
                .andExpect(jsonPath("$.reprogramaciones[0].anterior.idsTurno[0]").value(t20.getId()))
                .andExpect(jsonPath("$.reprogramaciones[0].anterior.total").value(80.0))
                .andExpect(jsonPath("$.reprogramaciones[0].nuevo.cancha").value("Cancha 2 · Fútbol 6"))
                .andExpect(jsonPath("$.reprogramaciones[0].nuevo.fecha").value(MANANA.toString()))
                .andExpect(jsonPath("$.reprogramaciones[0].nuevo.horarioTexto").value("9:00–10:00 p. m."))
                .andExpect(jsonPath("$.reprogramaciones[0].nuevo.cantidadTurnos").value(1))
                .andExpect(jsonPath("$.reprogramaciones[0].nuevo.total").value(90.0))
                .andExpect(jsonPath("$.reprogramaciones[1].actor").value("CLIENTE"))
                .andExpect(jsonPath("$.reprogramaciones[1].usuario").value("Cliente 7****321"))
                .andExpect(jsonPath("$.linea[-1:].detalle").value("Horario cambiado por el complejo: Cancha 1 · Fútbol 5 · "
                        + "21/11/2026 8:00–9:00 p. m. (S/ 80.00) → Cancha 2 · Fútbol 6 · 21/11/2026 9:00–10:00 p. m. "
                        + "(S/ 90.00)."));
        // El motivo del panel es una nota interna: el cliente no lo ve en su línea de tiempo.
        String publico = mvc.perform(get("/api/reservas/" + codigo).param("dni", DNI)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("El cliente llamó para jugar más tarde");

        // Otra vez (ADMIN), por el id numérico de la reserva: vuelve a la cancha 1.
        Long id = jdbc.queryForObject("SELECT id_reserva FROM reserva WHERE codigo_reserva = ?", Long.class, codigo);
        reprogramarPanel("ADMIN", String.valueOf(id), cuerpo(t19.getId(), "Mantenimiento de la cancha 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoReserva").value(codigo))
                .andExpect(jsonPath("$.reprogramaciones.length()").value(3))
                .andExpect(jsonPath("$.reprogramaciones[0].usuario").value("rhuaman"));
        assertThat(turnos.findById(t19.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(turnos.findById(t21.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);

        ArgumentCaptor<EventoDominio> eventos = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.RESERVA_REPROGRAMADA), eventos.capture());
        ReservaReprogramadaEvento ultimo = (ReservaReprogramadaEvento) eventos.getAllValues().get(2);
        assertThat(ultimo.actor()).isEqualTo("PANEL");
        assertThat(ultimo.motivo()).isEqualTo("Mantenimiento de la cancha 2");
        assertThat(ultimo.estado()).isEqualTo("PENDIENTE");
        ArgumentCaptor<EventoDominio> bitacora = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.AUDITORIA_ACCION), bitacora.capture());
        List<AccionAuditadaEvento> delPanel = bitacora.getAllValues().stream().map(AccionAuditadaEvento.class::cast)
                .filter(a -> "REPROGRAMAR_RESERVA".equals(a.accion()) && "RECEPCION".equals(a.rol())).toList();
        assertThat(delPanel).singleElement().satisfies(a -> {
            assertThat(a.actor()).isEqualTo("mtorres");
            assertThat(a.idEntidad()).isEqualTo(codigo);
            assertThat(a.detalle()).endsWith(" · El cliente llamó para jugar más tarde");
        });
    }

    @Test
    void elPanelPuedeCambiarDentroDeLasDosHorasPeroNoUnaReservaJugadaOCancelada() throws Exception {
        String pronto = reservarWeb(turno(3, HOY, 12).getId(), DNI).get("codigoReserva").asText();
        reprogramarPanel("RECEPCION", pronto, cuerpo(turno(3, HOY, 18).getId(), "Se cruzó con el campeonato"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("18:00"));

        jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA', asistencia = 'ASISTIO' WHERE codigo_reserva = ?", pronto);
        reprogramarPanel("ADMIN", pronto, cuerpo(turno(3, HOY, 20).getId(), "Pidió jugar otra hora"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_REPROGRAMABLE"))
                .andExpect(jsonPath("$.mensaje").value("Ya se registró la asistencia de esta reserva; ya no se puede cambiar el horario."));
        mvc.perform(get("/api/admin/reservas/" + pronto).header("Authorization", token("ADMIN")))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));

        String cancelada = reservarWeb(turno(1, MANANA, 9).getId(), "70123322").get("codigoReserva").asText();
        mvc.perform(post("/api/admin/reservas/" + cancelada + "/cancelar").header("Authorization", token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Lluvia intensa en el complejo\"}"))
                .andExpect(status().isOk());
        reprogramarPanel("ADMIN", cancelada, cuerpo(turno(1, MANANA, 10).getId(), "Reprogramar por lluvia"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_REPROGRAMABLE"));
        reprogramarPanel("ADMIN", "ALC-ZZZZZZ", cuerpo(turno(1, MANANA, 10).getId(), "No existe esta reserva"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validaElMotivoYLosTurnosYPideSesionDelPanel() throws Exception {
        Turno libre = turno(1, MANANA, 20);
        String codigo = reservarWeb(turno(1, MANANA, 19).getId(), DNI).get("codigoReserva").asText();
        String mensaje = "Cuéntanos el motivo del cambio (5 a 200 caracteres, sin enlaces ni todo en mayúsculas).";
        for (String motivo : new String[]{null, "", "    ", "Ok", "x".repeat(201), "Ver www.ejemplo.com",
                "CAMBIO PEDIDO POR EL CLIENTE"}) {
            reprogramarPanel("ADMIN", codigo, cuerpo(libre.getId(), motivo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
        }
        reprogramarPanel("ADMIN", codigo, "{\"idsTurno\": [], \"motivo\": \"Cambio pedido\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("idsTurno"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige entre 1 y 8 turnos."));
        // El panel puede tomar un turno que ya empezó y aún no termina, pero no uno que ya terminó.
        reprogramarPanel("ADMIN", codigo, cuerpo(turno(1, HOY, 9).getId(), "Cambio pedido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ese turno ya no está disponible."));

        mvc.perform(post("/api/admin/reservas/" + codigo + "/reprogramar").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(libre.getId(), "Cambio pedido")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reservas/" + codigo + "/reprogramar").header("Authorization", tokenCliente(DNI))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(libre.getId(), "Cambio pedido")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva_reprogramacion", Long.class)).isZero();
    }

    @Test
    void elPanelNoPuedeMoverUnPartidoQueYaEmpezoAunqueNadieMarcaraLaAsistencia() throws Exception {
        // Se reservó a las 8:00 para hoy a las 9:00; ahora son las 10:40 y nadie marcó la asistencia.
        fijarReloj(AHORA.withHour(8).withMinute(0));
        String jugada = reservarWeb(turno(1, HOY, 9).getId(), DNI).get("codigoReserva").asText();
        jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA', estado_pago = 'PAGADO_TOTAL', monto_pagado = monto_total "
                + "WHERE codigo_reserva = ?", jugada);
        fijarReloj(AHORA);

        mvc.perform(get("/api/admin/reservas/" + jugada).header("Authorization", token("ADMIN")))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
        reprogramarPanel("RECEPCION", jugada, cuerpo(turno(1, MANANA, 19).getId(), "No vino por la lluvia"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_REPROGRAMABLE"))
                .andExpect(jsonPath("$.mensaje").value("El partido de esta reserva ya empezó; ya no se puede cambiar el horario."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva_reprogramacion", Long.class)).isZero();
    }
}
