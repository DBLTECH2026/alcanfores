package pe.alcanfores.reservas.servicio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.reservas.modelo.ActorReprogramacion;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaReprogramacion;
import pe.alcanfores.reservas.modelo.Turno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas del cambio de horario (sin Spring): estados permitidos, plazo y límite del cliente, dinero y estado del pago. */
class ReglasReprogramacionTest {

    static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 21, 19, 0);

    private static Reserva reserva(EstadoReserva estado, EstadoPago pago, Asistencia asistencia, String pagado) {
        Turno t = new Turno();
        t.setId(1L);
        t.setFecha(INICIO.toLocalDate());
        t.setHoraInicio(INICIO.toLocalTime());
        t.setHoraFin(INICIO.toLocalTime().plusHours(1));
        Reserva r = new Reserva();
        r.setEstado(estado);
        r.agregarTurno(t, new BigDecimal("80.00"));
        r.setEstadoPago(pago);
        r.setAsistencia(asistencia);
        r.setMontoTotal(new BigDecimal("80.00"));
        r.setMontoAdelanto(new BigDecimal("40.00"));
        r.setMontoPagado(new BigDecimal(pagado));
        return r;
    }

    @ParameterizedTest(name = "{0} {1} {2} → {3}")
    @CsvSource({
            "PENDIENTE, SIN_PAGO, PENDIENTE, true",
            "PENDIENTE, EN_REVISION, PENDIENTE, true",
            "PENDIENTE, OBSERVADO, PENDIENTE, true",
            "CONFIRMADA, ADELANTO_PAGADO, PENDIENTE, true",
            "CONFIRMADA, PAGADO_TOTAL, PENDIENTE, true",
            "CONFIRMADA, PAGADO_TOTAL, ASISTIO, false",
            "CONFIRMADA, ADELANTO_PAGADO, NO_ASISTIO, false",
            "CANCELADA, REEMBOLSADO, PENDIENTE, false",
            "CANCELADA, SIN_PAGO, PENDIENTE, false"})
    void soloSeReprogramaUnaReservaActivaQueAunNoSeJuega(EstadoReserva estado, EstadoPago pago, Asistencia asistencia,
                                                          boolean esperado) {
        assertThat(ReglasReserva.reprogramable(reserva(estado, pago, asistencia, "0.00"), INICIO.minusDays(1)))
                .isEqualTo(esperado);
    }

    @Test
    void unPartidoQueYaEmpezoNoSeReprogramaAunqueNadieMarqueLaAsistencia() {
        Reserva r = reserva(EstadoReserva.CONFIRMADA, EstadoPago.PAGADO_TOTAL, Asistencia.PENDIENTE, "80.00");
        assertThat(ReglasReserva.reprogramable(r, INICIO.minusMinutes(1))).isTrue();
        assertThat(ReglasReserva.reprogramable(r, INICIO)).isFalse();
        assertThat(ReglasReserva.reprogramable(r, INICIO.plusDays(1))).isFalse();
    }

    @Test
    void elClienteTieneHastaDosHorasAntesYUnSoloCambio() {
        Reserva r = reserva(EstadoReserva.CONFIRMADA, EstadoPago.ADELANTO_PAGADO, Asistencia.PENDIENTE, "40.00");
        assertThat(ReglasReserva.puedeReprogramarCliente(r, INICIO.minusHours(2).minusMinutes(1), 0)).isTrue();
        assertThat(ReglasReserva.puedeReprogramarCliente(r, INICIO.minusHours(2), 0)).isFalse();
        assertThat(ReglasReserva.puedeReprogramarCliente(r, INICIO.minusDays(3), 1)).isFalse();
        assertThat(ReglasReserva.puedeReprogramarCliente(
                reserva(EstadoReserva.CANCELADA, EstadoPago.SIN_PAGO, Asistencia.PENDIENTE, "0.00"), INICIO.minusDays(3), 0))
                .isFalse();
    }

    @Test
    void loComprometidoSumaElMontoRealDelComprobanteEnRevisionYNoElAdelantoRecalculado() {
        assertThat(ReglasReserva.montoComprometido(
                reserva(EstadoReserva.CONFIRMADA, EstadoPago.ADELANTO_PAGADO, Asistencia.PENDIENTE, "40.00")))
                .isEqualByComparingTo("40.00");
        // El comprobante se registró por S/ 48.00; luego un cambio de horario bajó el adelanto a S/ 18.00.
        Reserva enRevision = reserva(EstadoReserva.PENDIENTE, EstadoPago.EN_REVISION, Asistencia.PENDIENTE, "0.00");
        enRevision.setMontoEnRevision(new BigDecimal("48.00"));
        enRevision.setMontoAdelanto(new BigDecimal("18.00"));
        assertThat(ReglasReserva.montoComprometido(enRevision)).isEqualByComparingTo("48.00");
        assertThat(ReglasReserva.montoComprometido(
                reserva(EstadoReserva.PENDIENTE, EstadoPago.OBSERVADO, Asistencia.PENDIENTE, "0.00")))
                .isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "comprometido {0} y adelanto nuevo {1} → {2}")
    @CsvSource({"0.00, 480.00, true", "20.00, 480.00, false", "479.99, 480.00, false", "480.00, 480.00, true",
            "500.00, 480.00, true"})
    void loPagadoDebeCubrirElAdelantoDelNuevoHorario(BigDecimal comprometido, BigDecimal adelanto, boolean esperado) {
        assertThat(ReglasReserva.adelantoCubierto(comprometido, adelanto)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} con {1} pagado de {2} → {3}")
    @CsvSource({
            "SIN_PAGO, 0.00, 90.00, SIN_PAGO",
            "EN_REVISION, 0.00, 90.00, EN_REVISION",
            "OBSERVADO, 0.00, 90.00, OBSERVADO",
            "ADELANTO_PAGADO, 40.00, 90.00, ADELANTO_PAGADO",
            "ADELANTO_PAGADO, 60.00, 60.00, PAGADO_TOTAL",
            "PAGADO_TOTAL, 80.00, 90.00, ADELANTO_PAGADO",
            "PAGADO_TOTAL, 80.00, 80.00, PAGADO_TOTAL"})
    void elEstadoDelPagoSeRecalculaConLoPagado(EstadoPago actual, BigDecimal pagado, BigDecimal total, EstadoPago esperado) {
        assertThat(ReglasReserva.estadoPagoTrasCambio(actual, pagado, total)).isEqualTo(esperado);
    }

    @Test
    void elHistorialGuardaAmbosHorariosYRecortaLosTextos() {
        ReservaReprogramacion.Horario antes = new ReservaReprogramacion.Horario(1L, "Cancha 1", LocalDate.of(2026, 11, 21),
                LocalTime.of(19, 0), LocalTime.of(21, 0), "7:00–9:00 p. m.", List.of(10L, 11L), new BigDecimal("160.00"));
        ReservaReprogramacion.Horario despues = new ReservaReprogramacion.Horario(2L, "C".repeat(70), LocalDate.of(2026, 11, 22),
                LocalTime.of(20, 0), LocalTime.of(21, 0), "8:00–9:00 p. m.", List.of(25L), new BigDecimal("90.00"));
        ReservaReprogramacion rp = new ReservaReprogramacion(5L, antes, despues, ActorReprogramacion.PANEL, "rhuaman",
                "m".repeat(250), LocalDateTime.of(2026, 11, 20, 10, 40));

        assertThat(rp.anterior()).isEqualTo(antes);
        assertThat(rp.getTurnosAnteriores()).isEqualTo("10,11");
        assertThat(rp.nuevo().idsTurno()).containsExactly(25L);
        assertThat(rp.nuevo().cancha()).hasSize(60);
        assertThat(rp.getMotivo()).hasSize(200);
        assertThat(ActorReprogramacion.PANEL.delCliente()).isFalse();
        assertThat(ActorReprogramacion.CANCHITA.delCliente()).isTrue();
        assertThat(ActorReprogramacion.CLIENTE.delCliente()).isTrue();
    }
}
