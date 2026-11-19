package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsProcessor;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CORS global con el orden fijado en {@link OrdenFiltros}: responde los preflight sin pasar por los demás filtros y
 * agrega las cabeceras CORS también a las respuestas de error del propio gateway. Si el {@code Origin} no está en
 * {@code FRONT_URL} responde 403 {@code ORIGEN_NO_PERMITIDO} con el cuerpo estándar {@code ErrorApi} (no vacío).
 */
public class FiltroCors implements WebFilter, Ordered {

    public static final String CODIGO = "ORIGEN_NO_PERMITIDO";
    public static final String MENSAJE = "Este sitio no tiene permiso para usar la API de Alcanfores. "
            + "Abre el sistema desde su dirección oficial.";

    private final CorsConfigurationSource origen;
    private final CorsProcessor procesador = new DefaultCorsProcessor();
    private final RespuestaError error;

    public FiltroCors(CorsConfigurationSource origen, RespuestaError error) {
        this.origen = origen;
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        boolean valido = procesador.process(origen.getCorsConfiguration(exchange), exchange);
        if (!valido) {
            return error.escribir(exchange, HttpStatus.FORBIDDEN, CODIGO, MENSAJE);
        }
        if (CorsUtils.isPreFlightRequest(exchange.getRequest())) {
            return Mono.empty();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.CORS;
    }
}
