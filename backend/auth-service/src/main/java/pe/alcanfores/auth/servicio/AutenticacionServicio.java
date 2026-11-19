package pe.alcanfores.auth.servicio;

import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.EditarMisDatosSolicitud;
import pe.alcanfores.auth.dto.LoginRespuesta;
import pe.alcanfores.auth.dto.LoginSolicitud;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.comun.seguridad.UsuarioToken;

/** Inicio de sesión y operaciones del propio usuario autenticado. */
public interface AutenticacionServicio {

    /**
     * Valida credenciales y emite el JWT. 401 {@code CREDENCIALES_INVALIDAS} (mismo mensaje exista o no el usuario),
     * 423 {@code USUARIO_BLOQUEADO} tras 5 fallos seguidos (15 min), 403 {@code USUARIO_INACTIVO}.
     */
    LoginRespuesta iniciarSesion(LoginSolicitud solicitud);

    UsuarioRespuesta usuarioActual(UsuarioToken token);

    void cambiarContrasena(UsuarioToken token, CambiarContrasenaSolicitud solicitud);

    /** El usuario del panel completa su celular y su correo (409 {@code CORREO_DUPLICADO} si el correo es de otro). */
    UsuarioRespuesta editarMisDatos(UsuarioToken token, EditarMisDatosSolicitud solicitud);
}
