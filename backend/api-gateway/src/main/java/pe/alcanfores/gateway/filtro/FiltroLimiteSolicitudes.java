package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pe.alcanfores.gateway.limite.LimitadorSolicitudes;
import reactor.core.publisher.Mono;

/**
 * Aplica el {@link LimitadorSolicitudes}: si la IP agotó su cupo responde 429 {@code DEMASIADAS_SOLICITUDES} con
 * {@code Retry-After}. Las respuestas permitidas llevan {@code X-RateLimit-Limit} y {@code X-RateLimit-Remaining}.
 */
@Component
public class FiltroLimiteSolicitudes implements WebFilter, Ordered {

    private final LimitadorSolicitudes limitador;
    private final RespuestaError error;

    public FiltroLimiteSolicitudes(LimitadorSolicitudes limitador, RespuestaError error) {
        this.limitador = limitador;
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ruta = RutaSolicitud.normalizar(exchange.getRequest().getPath().value());
        return limitador.reglaPara(exchange.getRequest().getMethod(), ruta)
                .map(regla -> {
                    LimitadorSolicitudes.Resultado r = limitador.consumir(regla, IpCliente.de(exchange));
                    HttpHeaders h = exchange.getResponse().getHeaders();
                    h.set("X-RateLimit-Limit", String.valueOf(r.limite()));
                    h.set("X-RateLimit-Remaining", String.valueOf(r.restantes()));
                    if (r.permitido()) {
                        return chain.filter(exchange);
                    }
                    h.set(HttpHeaders.RETRY_AFTER, String.valueOf(r.esperaSegundos()));
                    return error.escribir(exchange, HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_SOLICITUDES",
                            "Hiciste demasiadas solicitudes seguidas. Espera " + r.esperaSegundos()
                                    + (r.esperaSegundos() == 1 ? " segundo" : " segundos") + " y vuelve a intentarlo.");
                })
                .orElseGet(() -> chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.LIMITE_SOLICITUDES;
    }
}
