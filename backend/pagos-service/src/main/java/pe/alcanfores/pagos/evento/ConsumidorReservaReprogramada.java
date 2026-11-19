package pe.alcanfores.pagos.evento;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.pagos.config.MensajeriaConfig;
import pe.alcanfores.pagos.servicio.ReprogramacionReservaServicio;

/** Escucha {@code reserva.reprogramada} (cola {@code pagos.reserva-reprogramada}); 3 reintentos y luego cola de muertos. */
@Component
public class ConsumidorReservaReprogramada {

    private final ReprogramacionReservaServicio reprogramaciones;

    public ConsumidorReservaReprogramada(ReprogramacionReservaServicio reprogramaciones) {
        this.reprogramaciones = reprogramaciones;
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_REPROGRAMADA)
    public void alReprogramarReserva(ReservaReprogramadaEvento evento) {
        reprogramaciones.procesar(evento);
    }
}
