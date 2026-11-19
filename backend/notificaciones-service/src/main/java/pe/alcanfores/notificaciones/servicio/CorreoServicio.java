package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.notificaciones.dto.CorreoDtos.ConfigCorreoRespuesta;
import pe.alcanfores.notificaciones.dto.CorreoDtos.GuardarCorreoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.PruebaCorreoRespuesta;

/** Servidor de correo configurable desde el panel (sin la contraseña en ninguna respuesta). */
public interface CorreoServicio {

    ConfigCorreoRespuesta obtener();

    /** Guarda (clave vacía = conservar la anterior), invalida la caché del emisor y audita el cambio sin la clave. */
    ConfigCorreoRespuesta guardar(GuardarCorreoSolicitud solicitud);

    /** Envía YA un correo de prueba; 422 {@code CORREO_NO_ENVIADO} con la causa explicada si no sale. */
    PruebaCorreoRespuesta probar(String destino);
}
