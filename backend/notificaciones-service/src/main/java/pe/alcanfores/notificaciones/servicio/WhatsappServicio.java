package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.notificaciones.dto.ConexionWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.EstadoWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.MensajeRespuesta;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;

/** Gestión del WhatsApp del complejo en Evolution API desde el panel (solo ADMIN). */
public interface WhatsappServicio {

    EstadoWhatsappRespuesta estado();

    /** @param telefono opcional: si viene, Evolution devuelve también el código de emparejamiento. */
    ConexionWhatsappRespuesta conectar(String telefono);

    MensajeRespuesta desconectar();

    /** Envía (o simula) un mensaje de prueba y lo deja en la bandeja con el evento {@code whatsapp.prueba}. */
    NotificacionDetalle prueba(String telefono, String mensaje);
}
