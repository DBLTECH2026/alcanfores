package pe.alcanfores.reservas.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.reservas.config.MensajeriaConfig;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;

/** Consumidores de RabbitMQ (cada uno delega en un servicio transaccional e idempotente). */
@Component
@RequiredArgsConstructor
public class ConsumidorEventos {

    private final PagoEventosServicio pagos;
    private final TurnoServicio turnos;

    @RabbitListener(queues = MensajeriaConfig.COLA_PAGO_REGISTRADO)
    public void alRegistrarPago(PagoRegistradoEvento evento) {
        pagos.alRegistrarPago(evento);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_PAGO_VALIDADO)
    public void alValidarPago(PagoValidadoEvento evento) {
        pagos.alValidarPago(evento);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_PAGO_RECHAZADO)
    public void alRechazarPago(PagoRechazadoEvento evento) {
        pagos.alRechazarPago(evento);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_CANCHA_ACTUALIZADA)
    public void alActualizarCancha(CanchaActualizadaEvento evento) {
        turnos.alActualizarCancha(evento);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_CALENDARIO_ACTUALIZADO)
    public void alActualizarCalendario(CalendarioActualizadoEvento evento) {
        turnos.alActualizarCalendario(evento);
    }
}
