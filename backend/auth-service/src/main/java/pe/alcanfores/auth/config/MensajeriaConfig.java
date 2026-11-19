package pe.alcanfores.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.eventos.Eventos;

/** Cola {@value #COLA_AUDITORIA} enlazada a {@code auditoria.accion} (con DLQ, ver {@link Eventos#cola}). */
@Configuration
public class MensajeriaConfig {

    public static final String COLA_AUDITORIA = "auth.auditoria";

    @Bean
    Queue colaAuditoria() {
        return Eventos.cola(COLA_AUDITORIA);
    }

    @Bean
    Binding enlaceAuditoria(Queue colaAuditoria, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaAuditoria, intercambioEventos, Eventos.AUDITORIA_ACCION);
    }
}
