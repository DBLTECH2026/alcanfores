package pe.alcanfores.reservas.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.eventos.Eventos;

/** Colas propias de reservas-service (durables, con cola de mensajes muertos) enlazadas al exchange de eventos. */
@Configuration(proxyBeanMethods = false)
public class MensajeriaConfig {

    public static final String COLA_PAGO_REGISTRADO = "reservas.pago-registrado";
    public static final String COLA_PAGO_VALIDADO = "reservas.pago-validado";
    public static final String COLA_PAGO_RECHAZADO = "reservas.pago-rechazado";
    public static final String COLA_CANCHA_ACTUALIZADA = "reservas.cancha-actualizada";

    @Bean
    Queue colaPagoRegistrado() {
        return Eventos.cola(COLA_PAGO_REGISTRADO);
    }

    @Bean
    Queue colaPagoValidado() {
        return Eventos.cola(COLA_PAGO_VALIDADO);
    }

    @Bean
    Queue colaPagoRechazado() {
        return Eventos.cola(COLA_PAGO_RECHAZADO);
    }

    @Bean
    Queue colaCanchaActualizada() {
        return Eventos.cola(COLA_CANCHA_ACTUALIZADA);
    }

    @Bean
    Binding enlacePagoRegistrado(Queue colaPagoRegistrado, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaPagoRegistrado, intercambioEventos, Eventos.PAGO_REGISTRADO);
    }

    @Bean
    Binding enlacePagoValidado(Queue colaPagoValidado, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaPagoValidado, intercambioEventos, Eventos.PAGO_VALIDADO);
    }

    @Bean
    Binding enlacePagoRechazado(Queue colaPagoRechazado, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaPagoRechazado, intercambioEventos, Eventos.PAGO_RECHAZADO);
    }

    @Bean
    Binding enlaceCanchaActualizada(Queue colaCanchaActualizada, TopicExchange intercambioEventos) {
        return Eventos.enlace(colaCanchaActualizada, intercambioEventos, Eventos.CANCHA_ACTUALIZADA);
    }
}
