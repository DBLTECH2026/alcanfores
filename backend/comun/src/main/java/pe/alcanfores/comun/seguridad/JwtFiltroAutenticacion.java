package pe.alcanfores.comun.seguridad;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee {@code Authorization: Bearer <jwt>} y, si es válido, deja al administrador en el SecurityContext con
 * {@code ROLE_ADMIN} o {@code ROLE_RECEPCION}. Si el token venció, marca la solicitud para que el 401 lo diga.
 */
public class JwtFiltroAutenticacion extends OncePerRequestFilter {

    public static final String ATRIBUTO_ERROR = "alcanfores.jwt.error";
    private final JwtServicio jwt;

    public JwtFiltroAutenticacion(JwtServicio jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = req.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UsuarioToken u = jwt.validar(cabecera.substring(7).trim());
                var auth = new UsernamePasswordAuthenticationToken(u, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.rol())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                req.setAttribute(ATRIBUTO_ERROR, "Tu sesión venció. Vuelve a iniciar sesión.");
            } catch (JwtException | IllegalArgumentException e) {
                req.setAttribute(ATRIBUTO_ERROR, "El token de sesión no es válido.");
            }
        }
        chain.doFilter(req, res);
    }
}
