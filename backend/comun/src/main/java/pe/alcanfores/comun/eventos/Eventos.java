package pe.alcanfores.comun.eventos;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Nombres del bus de eventos (RabbitMQ). Cada servicio declara SUS colas con {@link #cola(String)} y las enlaza:
 * <pre>{@code
 * @Bean Queue colaPagoValidado() { return Eventos.cola("reservas.pago-validado"); }
 * @Bean Binding enlacePagoValidado(Queue colaPagoValidado, TopicExchange intercambioEventos) {
 *     return Eventos.enlace(colaPagoValidado, intercambioEventos, Eventos.PAGO_VALIDADO);
 * }
 * @RabbitListener(queues = "reservas.pago-validado") void alValidarPago(PagoValidadoEvento e) { ... }
 * }</pre>
 */
public final class Eventos {

    public static final String EXCHANGE = "alcanfores.eventos";
    public static final String DLX = "alcanfores.eventos.dlx";
    public static final String COLA_MUERTA = "alcanfores.eventos.muertos";

    public static final String RESERVA_REGISTRADA = "reserva.registrada";
    public static final String RESERVA_CONFIRMADA = "reserva.confirmada";
    public static final String RESERVA_CANCELADA = "reserva.cancelada";
    public static final String RESERVA_ASISTENCIA = "reserva.asistencia";
    public static final String PAGO_REGISTRADO = "pago.registrado";
    public static final String PAGO_VALIDADO = "pago.validado";
    public static final String PAGO_RECHAZADO = "pago.rechazado";
    public static final String COMPROBANTE_EMITIDO = "comprobante.emitido";
    public static final String CANCHA_ACTUALIZADA = "cancha.actualizada";
    public static final String AUDITORIA_ACCION = "auditoria.accion";

    private Eventos() {
    }

    /** Cola durable con cola de mensajes muertos (si el consumidor falla 3 veces el mensaje va a la DLX). */
    public static Queue cola(String nombre) {
        return QueueBuilder.durable(nombre).deadLetterExchange(DLX).deadLetterRoutingKey(nombre).build();
    }

    public static Binding enlace(Queue cola, TopicExchange intercambio, String routingKey) {
        return BindingBuilder.bind(cola).to(intercambio).with(routingKey);
    }
}
