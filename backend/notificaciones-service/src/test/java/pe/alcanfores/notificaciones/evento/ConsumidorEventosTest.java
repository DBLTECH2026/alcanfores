package pe.alcanfores.notificaciones.evento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoAdjunto;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.Recordatorio;
import pe.alcanfores.notificaciones.modelo.TipoAdjunto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumidorEventosTest extends PruebaIntegracion {

    @Autowired
    private ConsumidorEventos consumidor;

    @BeforeEach
    void pdfsDisponibles() {
        when(reservasCliente.constanciaPdf(anyString())).thenReturn(Datos.PDF);
        when(pagosCliente.comprobantePdf(anyLong())).thenReturn(Datos.PDF);
    }

    private Map<CanalNotificacion, Notificacion> porCanal() {
        List<Notificacion> todas = notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO);
        assertThat(todas).hasSize(2);
        return todas.stream().collect(Collectors.toMap(Notificacion::getCanal, Function.identity()));
    }

    @Test
    @DisplayName("reserva.registrada: correo + WhatsApp con código, adelanto, plazo, enlace y constancia adjunta")
    void reservaRegistrada() {
        consumidor.alRegistrarReserva(Datos.registrada());

        Map<CanalNotificacion, Notificacion> n = porCanal();
        Notificacion correo = n.get(CanalNotificacion.CORREO);
        Notificacion wsp = n.get(CanalNotificacion.WHATSAPP);
        assertThat(correo.getDestino()).isEqualTo("diego.ramos@example.com");
        assertThat(wsp.getDestino()).isEqualTo("51987654321");
        assertThat(correo.getEvento()).isEqualTo("reserva.registrada");
        assertThat(correo.getAsunto()).isEqualTo("Reserva ALC-7K3M9P registrada: completa tu pago");
        assertThat(correo.getEstado()).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(correo.getIntentos()).isEqualTo(1);
        assertThat(correo.getEnviadoEn()).isNotNull();
        assertThat(correo.getCuerpo()).contains("ALC-7K3M9P", "Cancha 1 · Fútbol 5", "S/ 40.00", "Plazo para pagar",
                "http://localhost:5186/mi-reserva?codigo=ALC-7K3M9P", "cid:logo-alcanfores");
        assertThat(wsp.getCuerpo()).contains("*Reserva registrada: completa tu pago*", "*Código:* ALC-7K3M9P",
                "*Adelanto para confirmar:* S/ 40.00", "*Plazo para pagar:*",
                "http://localhost:5186/mi-reserva?codigo=ALC-7K3M9P")
                .endsWith("Complejo Deportivo Los Alcanfores");
        Adjunto adjunto = correo.getAdjuntos().get(0);
        assertThat(adjunto.tipo()).isEqualTo(TipoAdjunto.CONSTANCIA);
        assertThat(adjunto.estado()).isEqualTo(EstadoAdjunto.ADJUNTADO);
        assertThat(adjunto.nombreArchivo()).isEqualTo("constancia-ALC-7K3M9P.pdf");
        // la constancia se descarga una sola vez para los dos canales
        verify(reservasCliente, times(1)).constanciaPdf(Datos.CODIGO);
    }

    @Test
    @DisplayName("Idempotencia: el mismo evento dos veces genera los avisos una sola vez")
    void idempotente() {
        ReservaRegistradaEvento e = Datos.registrada();
        consumidor.alRegistrarReserva(e);
        consumidor.alRegistrarReserva(e);

        assertThat(notificaciones.count()).isEqualTo(2);
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isTrue();
    }

    @Test
    @DisplayName("Sin PDF disponible el aviso sale igual y el adjunto queda NO_DISPONIBLE con el motivo")
    void sinPdf() {
        when(reservasCliente.constanciaPdf(anyString())).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de reservas no responde. Intenta en unos segundos."));

        consumidor.alRegistrarReserva(Datos.registrada());

        porCanal().values().forEach(n -> {
            assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.SIMULADO);
            assertThat(n.getAdjuntos()).singleElement().satisfies(a -> {
                assertThat(a.estado()).isEqualTo(EstadoAdjunto.NO_DISPONIBLE);
                assertThat(a.detalle()).contains("no responde");
            });
        });
    }

    @Test
    @DisplayName("pago.registrado (comprobante Yape): «recibimos tu comprobante» con los datos de reservas")
    void pagoRegistrado() {
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(Datos.reservaInterna("PENDIENTE", "80.00", null));

        consumidor.alRegistrarPago(Datos.pagoRegistrado("COMPROBANTE"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getAsunto()).isEqualTo("Recibimos tu comprobante de pago · ALC-7K3M9P");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo())
                .contains("*Recibimos tu comprobante*", "Yape", "*S/ 40.00*", "(adelanto)", "Lo validamos en minutos");
    }

    @Test
    @DisplayName("pago.registrado por pasarela o caja: no genera aviso pero queda procesado")
    void pagoRegistradoPasarela() {
        var e = Datos.pagoRegistrado("PASARELA");
        consumidor.alRegistrarPago(e);

        assertThat(notificaciones.count()).isZero();
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isTrue();
        verify(reservasCliente, never()).consultar(anyString());
    }

    @Test
    @DisplayName("pago.registrado sin respuesta de reservas: lanza para que RabbitMQ reintente y no lo marca procesado")
    void pagoRegistradoSinReservas() {
        when(reservasCliente.consultar(Datos.CODIGO)).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de reservas no responde. Intenta en unos segundos."));
        var e = Datos.pagoRegistrado("COMPROBANTE");

        assertThatThrownBy(() -> consumidor.alRegistrarPago(e)).isInstanceOf(NegocioExcepcion.class);
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isFalse();
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("pago.rechazado: observación, nuevo plazo y enlace para reenviar")
    void pagoRechazado() {
        when(reservasCliente.consultar(Datos.CODIGO)).thenReturn(
                Datos.reservaInterna("PENDIENTE", "80.00", LocalDateTime.of(2026, 11, 19, 22, 30)));

        consumidor.alRechazarPago(Datos.pagoRechazado());

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getCuerpo())
                .contains("La imagen no muestra el número de operación", "Reenviar comprobante", "jueves 19 de noviembre, 10:30 p. m.");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo())
                .contains("*Motivo:* La imagen no muestra el número de operación", "/mi-reserva?codigo=ALC-7K3M9P");
    }

    @Test
    @DisplayName("reserva.confirmada: confirmación + constancia + saldo + cómo llegar, y programa el recordatorio")
    void reservaConfirmada() {
        consumidor.alConfirmarReserva(Datos.confirmada());

        Map<CanalNotificacion, Notificacion> n = porCanal();
        Notificacion correo = n.get(CanalNotificacion.CORREO);
        assertThat(correo.getAsunto()).isEqualTo("Reserva confirmada · ALC-7K3M9P · 20/11/2026 7:00 p. m.");
        assertThat(correo.getCuerpo()).contains("Tu cancha está asegurada", "Saldo a pagar en el complejo: S/ 40.00",
                "Cómo llegar", "Carretera Central km 11.5", "https://maps.google.com/?q=alcanfores");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).contains("*Reserva confirmada*",
                "*Saldo a pagar en el complejo:* S/ 40.00", "*Cómo llegar:* Carretera Central km 11.5",
                "Mapa: https://maps.google.com/?q=alcanfores");
        assertThat(correo.getAdjuntos()).extracting(Adjunto::estado).containsExactly(EstadoAdjunto.ADJUNTADO);

        Recordatorio r = recordatorios.findByCodigoReserva(Datos.CODIGO).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoRecordatorio.PROGRAMADO);
        assertThat(r.getInicioReserva()).isEqualTo(Datos.INICIO);
        assertThat(r.getEnviarEn()).isEqualTo(Datos.INICIO.minusHours(2));
        assertThat(r.getDatos().saldoPendiente()).isEqualByComparingTo("40.00");
        assertThat(r.getDatos().reserva().cliente().nombres()).isEqualTo("Diego");
    }

    @Test
    @DisplayName("comprobante.emitido: boleta en PDF por los dos canales")
    void comprobanteEmitido() {
        consumidor.alEmitirComprobante(Datos.comprobante("BOLETA", "B001-00000012"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        Notificacion correo = n.get(CanalNotificacion.CORREO);
        assertThat(correo.getAsunto()).isEqualTo("Tu boleta electrónica B001-00000012 · ALC-7K3M9P");
        assertThat(correo.getCuerpo()).contains("B001-00000012", "S/ 40.00", "validez tributaria");
        assertThat(correo.getAdjuntos()).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoAdjunto.COMPROBANTE);
            assertThat(a.referencia()).isEqualTo("33");
            assertThat(a.nombreArchivo()).isEqualTo("B001-00000012.pdf");
            assertThat(a.estado()).isEqualTo(EstadoAdjunto.ADJUNTADO);
        });
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).contains("*Tu boleta electrónica*", "*B001-00000012*",
                "sin validez tributaria");
        verify(pagosCliente, times(1)).comprobantePdf(33L);
    }

    @Test
    @DisplayName("comprobante.emitido: nota de crédito")
    void notaCredito() {
        consumidor.alEmitirComprobante(Datos.comprobante("NOTA_CREDITO", "BC01-00000003"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getAsunto()).isEqualTo("Nota de crédito BC01-00000003 · ALC-7K3M9P");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).startsWith("*Nota de crédito BC01-00000003*");
    }

    @Test
    @DisplayName("reserva.cancelada: motivo y devolución; cancela el recordatorio programado")
    void reservaCancelada() {
        consumidor.alConfirmarReserva(Datos.confirmada());
        notificaciones.deleteAll();

        consumidor.alCancelarReserva(Datos.cancelada("CLIENTE", true, "40.00"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getAsunto()).isEqualTo("Reserva ALC-7K3M9P cancelada");
        assertThat(n.get(CanalNotificacion.CORREO).getCuerpo()).contains("Se lesionó un jugador del equipo",
                "Devolución de S/ 40.00", "Reservar otro turno");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).contains("fue cancelada por ti",
                "*Motivo:* Se lesionó un jugador del equipo", "*Devolución:* S/ 40.00");
        Recordatorio r = recordatorios.findByCodigoReserva(Datos.CODIGO).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoRecordatorio.CANCELADO);
        assertThat(r.getDetalle()).contains("CLIENTE");
    }

    @Test
    @DisplayName("reserva.cancelada por el sistema (venció el plazo) y sin devolución cuando hubo pago tardío")
    void canceladaPorSistema() {
        consumidor.alCancelarReserva(Datos.cancelada("SISTEMA", false, "0.00"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getCuerpo()).contains("Venció el plazo para pagar");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).contains("No recibimos el pago del adelanto a tiempo")
                .doesNotContain("Devolución");
    }

    @Test
    @DisplayName("Cancelación sin devolución con pago previo: explica la regla de horas")
    void canceladaSinDevolucion() {
        consumidor.alCancelarReserva(Datos.cancelada("ADMINISTRADOR", false, "40.00"));

        assertThat(porCanal().get(CanalNotificacion.WHATSAPP).getCuerpo())
                .contains("fue cancelada por el complejo", "menos de 24 horas de anticipación no tienen devolución");
    }

    @Test
    @DisplayName("reserva.asistencia ASISTIO: invitación a calificar con el enlace de reseña")
    void asistio() {
        consumidor.alRegistrarAsistencia(Datos.asistencia("ASISTIO"));

        Map<CanalNotificacion, Notificacion> n = porCanal();
        assertThat(n.get(CanalNotificacion.CORREO).getAsunto()).isEqualTo("¿Qué tal tu partido? Califica Cancha 1");
        assertThat(n.get(CanalNotificacion.CORREO).getCuerpo()).contains("http://localhost:5186/canchas/1?resena=ALC-7K3M9P",
                "Calificar la cancha");
        assertThat(n.get(CanalNotificacion.WHATSAPP).getCuerpo()).contains("http://localhost:5186/canchas/1?resena=ALC-7K3M9P");
    }

    @Test
    @DisplayName("reserva.asistencia NO_ASISTIO: no se envía nada")
    void noAsistio() {
        var e = Datos.asistencia("NO_ASISTIO");
        consumidor.alRegistrarAsistencia(e);

        assertThat(notificaciones.count()).isZero();
        assertThat(eventosProcesados.existsById(e.idEvento().toString())).isTrue();
    }
}
