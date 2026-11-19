package pe.alcanfores.comun.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.alcanfores.comun.error.ErrorApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Protege {@code /interno/**}: solo otro microservicio con la cabecera {@code X-Interno-Token} correcta entra
 * (con {@code ROLE_INTERNO}). El gateway nunca enruta estas rutas hacia afuera.
 */
public class InternoFiltro extends OncePerRequestFilter {

    public static final String CABECERA = "X-Interno-Token";
    public static final String PREFIJO = "/interno/";
    private final byte[] token;
    private final ObjectMapper json;

    public InternoFiltro(String token, ObjectMapper json) {
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith(PREFIJO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String recibido = req.getHeader(CABECERA);
        boolean valido = token.length > 0 && recibido != null
                && MessageDigest.isEqual(token, recibido.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            res.setStatus(403);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getOutputStream(),
                    ErrorApi.de(403, "SIN_PERMISO", "Ruta interna: solo para otros microservicios.", req.getRequestURI()));
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken("servicio-interno", null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNO")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }
}
