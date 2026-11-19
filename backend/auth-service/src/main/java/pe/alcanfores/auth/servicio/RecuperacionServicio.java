package pe.alcanfores.auth.servicio;

import pe.alcanfores.auth.dto.RecuperarDtos.EnvioRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.EnviarSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.RestablecerSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificacionRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificarSolicitud;

/**
 * «¿Olvidaste tu contraseña?» para clientes del portal y usuarios del panel: código de 6 dígitos por correo o WhatsApp
 * (10 min, 5 intentos, 3 envíos cada 15 min, reenviar tras 60 s) → permiso de un solo uso (10 min) → contraseña nueva.
 */
public interface RecuperacionServicio {

    /** Canales con el destino enmascarado; 404 {@code CUENTA_NO_ENCONTRADA} si no existe o está inactiva. */
    OpcionesRespuesta opciones(OpcionesSolicitud solicitud);

    EnvioRespuesta enviar(EnviarSolicitud solicitud);

    VerificacionRespuesta verificar(VerificarSolicitud solicitud);

    /** Cambia la contraseña, desbloquea la cuenta, invalida códigos y permisos y avisa por el mismo canal. */
    void restablecer(RestablecerSolicitud solicitud);
}
