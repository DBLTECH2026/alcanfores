package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cancelación por cliente y por el panel (regla de devolución) y expiración de reservas sin pago. */
class CancelacionYExpiracionTest extends PruebaIntegracion {

    @Autowired
    private ExpiracionServicio expiracion;
    @Autowired
    private PagoEventosServicio pagoEventos;

    @Test
    void elClienteCancelaSinPagoYSeLiberaElTurno() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 19);
        String codigo = reservarWeb(t.getId(), "70123321").get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"Cambio de planes del equipo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.cancelacion.canceladoPor").value("CLIENTE"))
                .andExpect(jsonPath("$.cancelacion.devolucion").value(false))
                .andExpect(jsonPath("$.cancelacion.montoDevolucion").value(0.00))
                .andExpect(jsonPath("$.puedeCancelar").value(false))
                .andExpect(jsonPath("$.linea[1].evento").value("CANCELADA"));
        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        verify(publicador).publicar(eq(Eventos.RESERVA_CANCELADA), org.mockito.ArgumentMatchers.any(ReservaCanceladaEvento.class));
        // El turno liberado se puede volver a reservar.
        reservarWeb(t.getId(), "70123322");
    }

    @Test
    void conPagoYMasDe24HorasHayDevolucionDeLoPagado() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY.plusDays(3), 19));
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"No completamos el equipo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion.devolucion").value(true))
                .andExpect(jsonPath("$.cancelacion.montoDevolucion").value(40.00));
        ArgumentCaptor<ReservaCanceladaEvento> evento = ArgumentCaptor.forClass(ReservaCanceladaEvento.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_CANCELADA), evento.capture());
        assertThat(evento.getValue().devolucion()).isTrue();
        assertThat(evento.getValue().montoDevolucion()).isEqualByComparingTo("40.00");
        assertThat(evento.getValue().canceladoPor()).isEqualTo("CLIENTE");
    }

    @Test
    void conPagoYMenosDe24HorasNoHayDevolucion() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY, 20));
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"No completamos el equipo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion.devolucion").value(false));
    }

    @Test
    void noSePuedeCancelarDespuesDelInicioNiDosVeces() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY, 12));
        fijarReloj(AHORA.plusHours(2));
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"Llegamos tarde al partido\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_YA_EMPEZO"));
    }

    @Test
    void validaElMotivoYElDniDeLaCancelacion() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"701\",\"motivo\":\"corto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[?(@.campo=='motivo')].mensaje").value("Cuéntanos el motivo (mínimo 10 caracteres)."));
    }

    @Test
    void elPanelCancelaYDecideLaDevolucion() throws Exception {
        String codigo = reservaConAdelanto(turno(2, HOY, 20));
        mvc.perform(post("/api/admin/reservas/" + codigo + "/cancelar").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Corte de luz programado en la zona\",\"devolucion\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.cancelacion.canceladoPor").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.cancelacion.devolucion").value(true))
                .andExpect(jsonPath("$.cancelacion.montoDevolucion").value(45.00));
        mvc.perform(post("/api/admin/reservas/" + codigo + "/cancelar").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Otra vez por error\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_CANCELABLE"));
    }

    @Test
    void laTareaExpiraLasReservasVencidasYLiberaElTurno() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 19);
        String vencida = reservarWeb(t.getId(), "70123321").get("codigoReserva").asText();
        String enRevision = reservarWeb(turno(2, HOY.plusDays(1), 19).getId(), "70123322").get("codigoReserva").asText();
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 1L, enRevision, "YAPE", "COMPROBANTE",
                new BigDecimal("45.00"), "ADELANTO"));

        fijarReloj(AHORA.plusMinutes(19));
        assertThat(expiracion.expirarVencidas()).isZero();
        fijarReloj(AHORA.plusMinutes(21));
        assertThat(expiracion.expirarVencidas()).isEqualTo(1);

        Reserva r = reservas.findByCodigoReserva(vencida).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.getVenceEn()).isNull();
        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(jdbc.queryForObject("SELECT cancelado_por FROM cancelacion WHERE id_reserva = ?", String.class, r.getId()))
                .isEqualTo("SISTEMA");
        assertThat(jdbc.queryForObject("SELECT motivo FROM cancelacion WHERE id_reserva = ?", String.class, r.getId()))
                .isEqualTo("Venció el plazo para pagar");
        assertThat(reservas.findByCodigoReserva(enRevision).orElseThrow().getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        ArgumentCaptor<ReservaCanceladaEvento> evento = ArgumentCaptor.forClass(ReservaCanceladaEvento.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_CANCELADA), evento.capture());
        assertThat(evento.getValue().canceladoPor()).isEqualTo("SISTEMA");
        assertThat(expiracion.expirarVencidas()).isZero();
    }

    /** Reserva web con el adelanto validado (por el consumidor de pago.validado). */
    private String reservaConAdelanto(Turno t) throws Exception {
        String codigo = reservarWeb(t.getId(), "70123321").get("codigoReserva").asText();
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, codigo, "YAPE", "COMPROBANTE",
                r.getMontoAdelanto(), "ADELANTO", "mtorres"));
        return codigo;
    }
}
