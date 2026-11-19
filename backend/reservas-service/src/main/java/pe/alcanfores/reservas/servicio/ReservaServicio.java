package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;

/** Reservas desde el portal público (cliente sin sesión: se identifica con código + DNI). */
public interface ReservaServicio {

    ReservaCreadaRespuesta registrar(RegistroReservaSolicitud solicitud);

    ReservaClienteDetalle consultar(String codigo, String dni);

    ReservaClienteDetalle cancelar(String codigo, CancelacionClienteSolicitud solicitud);

    /** Constancia PDF (bytes). */
    byte[] constancia(String codigo, String dni);
}
