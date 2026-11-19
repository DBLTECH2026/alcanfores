package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReprogramacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;

/** Reservas desde el portal público (cliente sin sesión: se identifica con código + DNI). */
public interface ReservaServicio {

    ReservaCreadaRespuesta registrar(RegistroReservaSolicitud solicitud);

    ReservaClienteDetalle consultar(String codigo, String dni);

    ReservaClienteDetalle cancelar(String codigo, CancelacionClienteSolicitud solicitud);

    /**
     * Cambia el horario conservando lo pagado (hasta 2 horas antes y una sola vez por reserva).
     *
     * @param conversacionCanchita conversación de Canchita si la llamada trae un token interno válido (actor «Canchita
     *                             (IA)» en la bitácora); {@code null} si la hace el cliente
     */
    ReservaClienteDetalle reprogramar(String codigo, ReprogramacionClienteSolicitud solicitud, String conversacionCanchita);

    /** Constancia PDF (bytes). */
    byte[] constancia(String codigo, String dni);
}
