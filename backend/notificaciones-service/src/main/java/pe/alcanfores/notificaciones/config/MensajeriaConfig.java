package pe.alcanfores.notificaciones.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.eventos.Eventos;

/**
 * Colas propias del servicio (durables, con DLX): una por evento que se convierte en aviso al cliente.
 * El exchange {@code alcanfores.eventos} y la DLX los declara la autoconfiguración de {@code comun}.
 */
@Configuration
public class MensajeriaConfig {

    public static final String COLA_RESERVA_REGISTRADA = "notificaciones.reserva-registrada";
    public static final String COLA_RESERVA_CONFIRMADA = "notificaciones.reserva-confirmada";
    public static final String COLA_RESERVA_CANCELADA = "notificaciones.reserva-cancelada";
    public static final String COLA_RESERVA_ASISTENCIA = "notificaciones.reserva-asistencia";
    public static final String COLA_PAGO_REGISTRADO = "notificaciones.pago-registrado";
    public static final String COLA_PAGO_RECHAZADO = "notificaciones.pago-rechazado";
    public static final String COLA_COMPROBANTE_EMITIDO = "notificaciones.comprobante-emitido";

    @Bean
    Queue colaReservaRegistrada() {
        return Eventos.cola(COLA_RESERVA_REGISTRADA);
    }

    @Bean
    Binding enlaceReservaRegistrada(Queue colaReservaRegistrada, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaReservaRegistrada, intercambioEventos, Eventos.RESERVA_REGISTRADA);
    }

    @Bean
    Queue colaReservaConfirmada() {
        return Eventos.cola(COLA_RESERVA_CONFIRMADA);
    }

    @Bean
    Binding enlaceReservaConfirmada(Queue colaReservaConfirmada, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaReservaConfirmada, intercambioEventos, Eventos.RESERVA_CONFIRMADA);
    }

    @Bean
    Queue colaReservaCancelada() {
        return Eventos.cola(COLA_RESERVA_CANCELADA);
    }

    @Bean
    Binding enlaceReservaCancelada(Queue colaReservaCancelada, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaReservaCancelada, intercambioEventos, Eventos.RESERVA_CANCELADA);
    }

    @Bean
    Queue colaReservaAsistencia() {
        return Eventos.cola(COLA_RESERVA_ASISTENCIA);
    }

    @Bean
    Binding enlaceReservaAsistencia(Queue colaReservaAsistencia, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaReservaAsistencia, intercambioEventos, Eventos.RESERVA_ASISTENCIA);
    }

    @Bean
    Queue colaPagoRegistrado() {
        return Eventos.cola(COLA_PAGO_REGISTRADO);
    }

    @Bean
    Binding enlacePagoRegistrado(Queue colaPagoRegistrado, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaPagoRegistrado, intercambioEventos, Eventos.PAGO_REGISTRADO);
    }

    @Bean
    Queue colaPagoRechazado() {
        return Eventos.cola(COLA_PAGO_RECHAZADO);
    }

    @Bean
    Binding enlacePagoRechazado(Queue colaPagoRechazado, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaPagoRechazado, intercambioEventos, Eventos.PAGO_RECHAZADO);
    }

    @Bean
    Queue colaComprobanteEmitido() {
        return Eventos.cola(COLA_COMPROBANTE_EMITIDO);
    }

    @Bean
    Binding enlaceComprobanteEmitido(Queue colaComprobanteEmitido, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaComprobanteEmitido, intercambioEventos, Eventos.COMPROBANTE_EMITIDO);
    }
}
