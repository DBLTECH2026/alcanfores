package pe.alcanfores.auth.mapeador;

import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.dto.UsuarioSesion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

/** Conversión entre la entidad {@link Administrador} y sus vistas (sesión, lista, token). */
public final class UsuarioMapeador {

    private UsuarioMapeador() {
    }

    public static UsuarioSesion aSesion(Administrador a) {
        return new UsuarioSesion(a.getId(), a.getNombres(), a.getApellidos(), a.getUsuario(), a.getCorreo(), a.getTelefono(),
                a.getRol());
    }

    public static UsuarioRespuesta aRespuesta(Administrador a) {
        boolean bloqueado = a.estaBloqueado(Tiempo.ahora());
        return new UsuarioRespuesta(a.getId(), a.getNombres(), a.getApellidos(), a.getUsuario(), a.getCorreo(), a.getTelefono(),
                a.getRol(),
                a.getEstado(), bloqueado, bloqueado ? a.getBloqueadoHasta() : null, a.getUltimoAcceso(), a.getCreadoEn(),
                a.getActualizadoEn());
    }

    public static UsuarioToken aToken(Administrador a) {
        return new UsuarioToken(a.getId(), a.getUsuario(), a.nombreCompleto(), a.getRol().name());
    }
}
