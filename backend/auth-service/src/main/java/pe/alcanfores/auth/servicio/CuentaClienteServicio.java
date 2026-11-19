package pe.alcanfores.auth.servicio;

import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.ClienteSesion;
import pe.alcanfores.auth.dto.EditarClienteSolicitud;
import pe.alcanfores.auth.dto.LoginClienteRespuesta;
import pe.alcanfores.auth.dto.LoginClienteSolicitud;
import pe.alcanfores.auth.dto.RegistroClienteSolicitud;
import pe.alcanfores.comun.seguridad.UsuarioToken;

/** Cuentas de clientes del portal («Mis reservas»). Reservar sin cuenta sigue funcionando igual. */
public interface CuentaClienteServicio {

    /** Crea la cuenta y deja la sesión iniciada. 409 {@code DNI_REGISTRADO} / {@code CORREO_REGISTRADO}. */
    LoginClienteRespuesta registrar(RegistroClienteSolicitud solicitud);

    /**
     * Ingreso con DNI o correo. 401 {@code CREDENCIALES_INVALIDAS} (mismo mensaje exista o no la cuenta),
     * 423 {@code USUARIO_BLOQUEADO} tras 5 fallos seguidos (15 min), 403 {@code USUARIO_INACTIVO}.
     */
    LoginClienteRespuesta iniciarSesion(LoginClienteSolicitud solicitud);

    ClienteSesion actual(UsuarioToken token);

    ClienteSesion editar(UsuarioToken token, EditarClienteSolicitud solicitud);

    void cambiarContrasena(UsuarioToken token, CambiarContrasenaSolicitud solicitud);
}
