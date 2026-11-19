package pe.alcanfores.pagos.evento;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.pagos.config.MensajeriaConfig;
import pe.alcanfores.pagos.servicio.CancelacionReservaServicio;

/** Escucha {@code reserva.cancelada} (cola {@code pagos.reserva-cancelada}); 3 reintentos y luego cola de muertos. */
@Component
public class ConsumidorReservaCancelada {

    private final CancelacionReservaServicio cancelaciones;

    public ConsumidorReservaCancelada(CancelacionReservaServicio cancelaciones) {
        this.cancelaciones = cancelaciones;
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_CANCELADA)
    public void alCancelarReserva(ReservaCanceladaEvento evento) {
        cancelaciones.procesar(evento);
    }
}
