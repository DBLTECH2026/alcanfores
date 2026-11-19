package pe.alcanfores.auth.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.auth.dto.CrearUsuarioSolicitud;
import pe.alcanfores.auth.dto.EditarUsuarioSolicitud;
import pe.alcanfores.auth.dto.FiltroUsuarios;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;

/** Gestión de usuarios del panel (solo ADMIN). Cada cambio queda en la bitácora. */
public interface UsuarioServicio {

    PaginaRespuesta<UsuarioRespuesta> listar(FiltroUsuarios filtro, Pageable pagina);

    UsuarioRespuesta obtener(Long id);

    UsuarioRespuesta crear(CrearUsuarioSolicitud solicitud);

    /** Un ADMIN no puede quitarse su propio rol ADMIN y siempre debe quedar al menos un ADMIN activo. */
    UsuarioRespuesta editar(Long id, EditarUsuarioSolicitud solicitud, UsuarioToken actor);

    /** Un ADMIN no puede desactivarse a sí mismo ni desactivar al último ADMIN activo. */
    UsuarioRespuesta cambiarEstado(Long id, EstadoUsuario estado, UsuarioToken actor);

    /** Asigna una contraseña nueva y desbloquea la cuenta. */
    void restablecerContrasena(Long id, String nueva);
}
