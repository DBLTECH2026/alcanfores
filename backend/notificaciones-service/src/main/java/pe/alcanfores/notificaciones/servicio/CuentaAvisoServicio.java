package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.notificaciones.dto.CorreoDtos.CodigoInternoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.EnvioCodigoRespuesta;

/** Avisos de la cuenta que pide auth-service: código para cambiar la contraseña y «tu contraseña se cambió». */
public interface CuentaAvisoServicio {

    /** Envía en el momento (síncrono); en la bandeja el código queda enmascarado («******»). */
    EnvioCodigoRespuesta enviar(CodigoInternoSolicitud solicitud);
}
