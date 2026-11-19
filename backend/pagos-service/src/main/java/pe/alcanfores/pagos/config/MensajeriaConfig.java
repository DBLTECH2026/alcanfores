package pe.alcanfores.pagos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.eventos.Eventos;

/** Colas que consume pagos-service (con cola de mensajes muertos). */
@Configuration
public class MensajeriaConfig {

    public static final String COLA_RESERVA_CANCELADA = "pagos.reserva-cancelada";

    @Bean
    Queue colaReservaCancelada() {
        return Eventos.cola(COLA_RESERVA_CANCELADA);
    }

    @Bean
    Binding enlaceReservaCancelada(Queue colaReservaCancelada, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaReservaCancelada, intercambioEventos, Eventos.RESERVA_CANCELADA);
    }
}
