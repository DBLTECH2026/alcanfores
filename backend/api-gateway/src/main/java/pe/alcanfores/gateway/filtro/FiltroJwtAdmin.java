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
import pe.alcanfores.comun.seguridad.UsuarioToken;
import reactor.core.publisher.Mono;

/**
 * Primera barrera del panel y de «Mis reservas»: en {@code /api/admin/**} y {@code /api/cliente/**} exige un JWT válido y
 * vigente (401 temprano, sin molestar al servicio). En {@code /api/admin/**} además rechaza con 403 {@code SIN_PERMISO} un
 * token de cliente (rol CLIENTE). Cada servicio lo vuelve a validar y revisa el rol (defensa en profundidad). El login,
 * el registro de clientes y el catálogo público no pasan por aquí (la reseña con sesión la valida catalogo-service).
 */
@Component
public class FiltroJwtAdmin implements WebFilter, Ordered {

    static final String PREFIJO = "/api/admin";
    static final String PREFIJO_CLIENTE = "/api/cliente";
    private final JwtServicio jwt;
    private final RespuestaError error;

    public FiltroJwtAdmin(JwtServicio jwt, RespuestaError error) {
        this.jwt = jwt;
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ruta = RutaSolicitud.normalizar(exchange.getRequest().getPath().value());
        boolean panel = RutaSolicitud.empiezaCon(ruta, PREFIJO);
        if (!panel && !RutaSolicitud.empiezaCon(ruta, PREFIJO_CLIENTE)) {
            return chain.filter(exchange);
        }
        String cabecera = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7) || cabecera.substring(7).isBlank()) {
            return noAutenticado(exchange, "Inicia sesión para continuar.");
        }
        try {
            UsuarioToken usuario = jwt.validar(cabecera.substring(7).trim());
            if (panel && usuario.esCliente()) {
                return error.escribir(exchange, HttpStatus.FORBIDDEN, "SIN_PERMISO", "Tu rol no tiene permiso para esta acción.");
            }
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
