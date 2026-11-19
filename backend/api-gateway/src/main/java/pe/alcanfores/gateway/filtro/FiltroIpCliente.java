package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Deja {@code X-Forwarded-For} con un solo valor, la IP del cliente calculada con {@link IpCliente}; luego el gateway
 * le agrega la del salto ({@code trusted-proxies}). Así la bitácora y la auditoría de {@code comun} (que toman el
 * primer valor) guardan la misma IP que usa el limitador de solicitudes. En producción el proxy inverso debe escribir
 * esa cabecera con la IP real ({@code $remote_addr}), no agregarla a lo que mande el cliente.
 */
@Component
public class FiltroIpCliente implements WebFilter, Ordered {

    static final String CABECERA = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = IpCliente.de(exchange);
        return chain.filter(exchange.mutate().request(r -> r.headers(h -> h.set(CABECERA, ip))).build());
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.IP_CLIENTE;
    }
}
