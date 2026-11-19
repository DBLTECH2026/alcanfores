package pe.alcanfores.gateway.filtro;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pe.alcanfores.comun.seguridad.JwtServicio;
import reactor.core.publisher.Mono;

/**
 * Primera barrera del panel: en {@code /api/admin/**} exige un JWT válido y vigente (401 temprano, sin molestar al
 * servicio). Cada servicio lo vuelve a validar y revisa el rol (defensa en profundidad). {@code /api/auth/login} es
 * público y no pasa por aquí.
 */
@Component
public class FiltroJwtAdmin implements WebFilter, Ordered {

    static final String PREFIJO = "/api/admin";
    private final JwtServicio jwt;
    private final RespuestaError error;

    public FiltroJwtAdmin(JwtServicio jwt, RespuestaError error) {
        this.jwt = jwt;
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ruta = RutaSolicitud.normalizar(exchange.getRequest().getPath().value());
        if (!RutaSolicitud.empiezaCon(ruta, PREFIJO)) {
            return chain.filter(exchange);
        }
        String cabecera = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7) || cabecera.substring(7).isBlank()) {
            return noAutenticado(exchange, "Inicia sesión para continuar.");
        }
        try {
            jwt.validar(cabecera.substring(7).trim());
            return chain.filter(exchange);
        } catch (ExpiredJwtException e) {
            return noAutenticado(exchange, "Tu sesión venció. Vuelve a iniciar sesión.");
        } catch (JwtException | IllegalArgumentException e) {
            return noAutenticado(exchange, "El token de sesión no es válido.");
        }
    }

    private Mono<Void> noAutenticado(ServerWebExchange exchange, String mensaje) {
        return error.escribir(exchange, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", mensaje);
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.JWT_ADMIN;
    }
}
