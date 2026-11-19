package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;

/**
 * Convierte cada evento de dominio en avisos al cliente (correo + WhatsApp). Todos los métodos son idempotentes:
 * un evento con un {@code idEvento} ya procesado no genera avisos de nuevo.
 */
public interface AvisoServicio {

    void alRegistrarReserva(ReservaRegistradaEvento evento);

    void alRegistrarPago(PagoRegistradoEvento evento);

    void alRechazarPago(PagoRechazadoEvento evento);

    void alConfirmarReserva(ReservaConfirmadaEvento evento);

    void alEmitirComprobante(ComprobanteEmitidoEvento evento);

    void alCancelarReserva(ReservaCanceladaEvento evento);

    void alRegistrarAsistencia(AsistenciaRegistradaEvento evento);
}
