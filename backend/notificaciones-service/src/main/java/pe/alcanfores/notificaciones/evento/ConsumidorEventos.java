package pe.alcanfores.notificaciones.evento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.notificaciones.config.MensajeriaConfig;
import pe.alcanfores.notificaciones.servicio.AvisoServicio;

/**
 * Escucha las colas propias del servicio y delega en {@link AvisoServicio} (que es idempotente). Si un método lanza
 * excepción, el listener reintenta 3 veces y luego el mensaje pasa a la cola de muertos ({@code alcanfores.eventos.dlx}).
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);
    private final AvisoServicio avisos;

    public ConsumidorEventos(AvisoServicio avisos) {
        this.avisos = avisos;
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_REGISTRADA)
    public void alRegistrarReserva(ReservaRegistradaEvento e) {
        log.info("reserva.registrada {} ({})", e.reserva().codigoReserva(), e.idEvento());
        avisos.alRegistrarReserva(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_PAGO_REGISTRADO)
    public void alRegistrarPago(PagoRegistradoEvento e) {
        log.info("pago.registrado {} ({})", e.codigoReserva(), e.idEvento());
        avisos.alRegistrarPago(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_PAGO_RECHAZADO)
    public void alRechazarPago(PagoRechazadoEvento e) {
        log.info("pago.rechazado {} ({})", e.codigoReserva(), e.idEvento());
        avisos.alRechazarPago(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_CONFIRMADA)
    public void alConfirmarReserva(ReservaConfirmadaEvento e) {
        log.info("reserva.confirmada {} ({})", e.reserva().codigoReserva(), e.idEvento());
        avisos.alConfirmarReserva(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_COMPROBANTE_EMITIDO)
    public void alEmitirComprobante(ComprobanteEmitidoEvento e) {
        log.info("comprobante.emitido {} ({})", e.serieNumero(), e.idEvento());
        avisos.alEmitirComprobante(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_CANCELADA)
    public void alCancelarReserva(ReservaCanceladaEvento e) {
        log.info("reserva.cancelada {} ({})", e.reserva().codigoReserva(), e.idEvento());
        avisos.alCancelarReserva(e);
    }

    @RabbitListener(queues = MensajeriaConfig.COLA_RESERVA_ASISTENCIA)
    public void alRegistrarAsistencia(AsistenciaRegistradaEvento e) {
        log.info("reserva.asistencia {} {} ({})", e.reserva().codigoReserva(), e.asistencia(), e.idEvento());
        avisos.alRegistrarAsistencia(e);
    }
}
