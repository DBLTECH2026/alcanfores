package pe.alcanfores.auth.evento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.alcanfores.auth.config.MensajeriaConfig;
import pe.alcanfores.auth.servicio.BitacoraServicio;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;

/**
 * Consume {@code auditoria.accion} de todos los microservicios y lo guarda en la bitácora central. Es idempotente:
 * si RabbitMQ reentrega el mismo {@code idEvento}, no se duplica la fila.
 */
@Component
public class ConsumidorAuditoria {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorAuditoria.class);

    private final BitacoraServicio bitacora;

    public ConsumidorAuditoria(BitacoraServicio bitacora) {
        this.bitacora = bitacora;
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_AUDITORIA)
    public void alRecibirAccion(AccionAuditadaEvento evento) {
        if (evento == null || evento.idEvento() == null) {
            // Sin identificador no se puede garantizar idempotencia: va directo a la cola de mensajes muertos.
            throw new AmqpRejectAndDontRequeueException("Evento de auditoría sin idEvento");
        }
        boolean guardado = bitacora.registrarEvento(evento);
        if (guardado) {
            log.info("Bitácora: {} {} por {} desde {}", evento.servicio(), evento.accion(), evento.actor(), evento.ip());
        }
    }
}
