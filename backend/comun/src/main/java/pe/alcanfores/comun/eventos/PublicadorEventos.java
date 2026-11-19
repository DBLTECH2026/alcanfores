package pe.alcanfores.comun.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publica eventos de dominio en el exchange {@value Eventos#EXCHANGE}. Publicar DESPUÉS del commit
 * ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}) para no anunciar algo que luego se revierte.
 */
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
    private final RabbitTemplate rabbit;

    public PublicadorEventos(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publicar(String routingKey, EventoDominio evento) {
        try {
            rabbit.convertAndSend(Eventos.EXCHANGE, routingKey, evento);
            log.info("Evento publicado {} id={}", routingKey, evento.idEvento());
        } catch (AmqpException e) {
            // El negocio ya se guardó: se registra el fallo para reintentarlo desde el panel/logs sin romper la respuesta.
            log.error("No se pudo publicar el evento {} id={}: {}", routingKey, evento.idEvento(), e.getMessage());
        }
    }
}
