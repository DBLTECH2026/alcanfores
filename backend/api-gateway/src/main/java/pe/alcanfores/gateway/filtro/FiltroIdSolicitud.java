package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Genera el {@code X-Request-Id} (o respeta el que manda el front si es válido), lo envía a los servicios y lo
 * devuelve en la respuesta: así un error del panel se puede rastrear en los logs de todos los microservicios.
 */
@Component
public class FiltroIdSolicitud implements WebFilter, Ordered {

    public static final String CABECERA = "X-Request-Id";
    public static final String ATRIBUTO = "alcanfores.idSolicitud";
    private static final Pattern VALIDO = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(CABECERA);
        String id = recibido != null && VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
        exchange.getAttributes().put(ATRIBUTO, id);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CABECERA, id);
            return Mono.empty();
        });
        ServerWebExchange conId = exchange.mutate().request(r -> r.headers(h -> h.set(CABECERA, id))).build();
        return chain.filter(conId);
    }

    public static String de(ServerWebExchange exchange) {
        Object id = exchange.getAttribute(ATRIBUTO);
        return id == null ? "-" : id.toString();
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.ID_SOLICITUD;
    }
}
