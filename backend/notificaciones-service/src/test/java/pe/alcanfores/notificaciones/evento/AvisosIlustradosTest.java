package pe.alcanfores.notificaciones.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.TipoAdjunto;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Tarjetas ilustradas en cada aviso, «¡Terminaste de pagar tu cancha!» y la tarjeta del recordatorio según el deporte. */
class AvisosIlustradosTest extends PruebaIntegracion {

    @Autowired
    private ConsumidorEventos consumidor;
    @Autowired
    private RecordatorioServicio recordatoriosServicio;

    private static PagoValidadoEvento pagoValidado(String tipoPago) {
        return new PagoValidadoEvento(UUID.randomUUID(), LocalDateTime.of(2026, 11, 20, 18, 40), 9L, Datos.CODIGO, "EFECTIVO",
                "CAJA", new BigDecimal("40.00"), tipoPago, "rhuaman");
    }

    private List<Notificacion> avisos() {
        return notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO);
    }

    @Test
    @DisplayName("pago.validado SALDO: «¡Terminaste de pagar tu cancha!» por los dos canales con la tarjeta saldo-pagado")
    void saldoPagado() {
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CONFIRMADA", "0.00", null));

        consumidor.alValidarPago(pagoValidado("SALDO"));

        assertThat(avisos()).hasSize(2).allSatisfy(n -> {
            assertThat(n.getEvento()).isEqualTo("pago.validado");
            assertThat(n.getAsunto()).isEqualTo("¡Terminaste de pagar tu cancha! · ALC-7K3M9P");
            assertThat(n.getAdjuntos()).singleElement().satisfies(a -> {
                assertThat(a.tipo()).isEqualTo(TipoAdjunto.IMAGEN);
                assertThat(a.referencia()).isEqualTo("saldo-pagado");
            });
        });
        Notificacion wsp = avisos().stream().filter(n -> n.getCanal() == CanalNotificacion.WHATSAPP).findFirst().orElseThrow();
        assertThat(wsp.getCuerpo()).startsWith("*¡Terminaste de pagar tu cancha!*")
                .contains("*Cancha:* Cancha 1 · Fútbol 5", "*Fecha:* Viernes 20 de noviembre de 2026", "*Hora:* 7:00–8:00 p. m.",
                        "*Saldo:* S/ 0.00");
        Notificacion correo = avisos().stream().filter(n -> n.getCanal() == CanalNotificacion.CORREO).findFirst().orElseThrow();
        assertThat(correo.getCuerpo()).contains("¡Terminaste de pagar tu cancha!", "Saldo S/ 0.00", "cid:tarjeta-aviso");
    }

    @Test
    @DisplayName("pago.validado ADELANTO o TOTAL: sin aviso (ya salen la confirmación y el comprobante), pero queda procesado")
    void otrosPagosSinAviso() {
        PagoValidadoEvento e = pagoValidado("ADELANTO");
        consumidor.alValidarPago(e);
        consumidor.alValidarPago(pagoValidado("TOTAL"));

        assertThat(notificaciones.count()).isZero();
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isTrue();
    }

    @Test
    @DisplayName("Asistencia ASISTIO: invitación a calificar con la tarjeta asistencia-confirmada")
    void asistencia() {
        consumidor.alRegistrarAsistencia(Datos.asistencia("ASISTIO"));

        assertThat(avisos()).hasSize(2).allSatisfy(n ->
                assertThat(n.getAdjuntos()).extracting(Adjunto::referencia).containsExactly("asistencia-confirmada"));
    }

    @Test
    @DisplayName("Recordatorio: tarjeta según el deporte (vóley y multiuso → vóley, básquet, fútbol)")
    void tarjetaDelRecordatorio() {
        assertThat(TipoAviso.RECORDATORIO.tarjeta("FUTBOL_7")).isEqualTo("recordatorio-futbol");
        assertThat(TipoAviso.RECORDATORIO.tarjeta("VOLEY")).isEqualTo("recordatorio-voley");
        assertThat(TipoAviso.RECORDATORIO.tarjeta("MULTIUSO")).isEqualTo("recordatorio-voley");
        assertThat(TipoAviso.RECORDATORIO.tarjeta("BASQUET")).isEqualTo("recordatorio-basquet");
        assertThat(TipoAviso.RESERVA_CANCELADA.tarjeta("VOLEY")).isEqualTo("reserva-cancelada");
        assertThat(TipoAviso.PRUEBA_WHATSAPP.tarjeta(null)).isNull();

        ClienteContacto c = Datos.cliente();
        DatosReserva voley = new DatosReserva(12L, Datos.CODIGO, 3L, "Cancha 3", "MULTIUSO", Datos.FECHA, Datos.HORA,
                Datos.HORA.plusHours(1), new BigDecimal("60.00"), new BigDecimal("30.00"), new BigDecimal("30.00"), c,
                null, 1);
        Datos.reloj(Datos.INICIO.minusDays(1));
        recordatoriosServicio.programar(voley, new BigDecimal("30.00"), List.of(120), Datos.INICIO.minusDays(2));
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("CONFIRMADA", "30.00", null));
        Datos.reloj(Datos.INICIO.minusHours(2));

        recordatoriosServicio.ejecutarPendientes();

        assertThat(avisos()).hasSize(2).allSatisfy(n ->
                assertThat(n.getAdjuntos()).extracting(Adjunto::referencia).containsExactly("recordatorio-voley"));
    }
}
