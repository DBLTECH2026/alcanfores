package pe.alcanfores.comun.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.Optional;

/** Acceso al administrador autenticado de la solicitud actual. */
public final class SeguridadUtil {

    private SeguridadUtil() {
    }

    public static Optional<UsuarioToken> actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioToken u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static UsuarioToken requerido() {
        return actual().orElseThrow(() -> new NegocioExcepcion(401, "NO_AUTENTICADO", "Inicia sesión para continuar."));
    }
}
