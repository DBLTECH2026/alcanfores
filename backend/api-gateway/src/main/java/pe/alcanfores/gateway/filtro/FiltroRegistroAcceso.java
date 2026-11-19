package pe.alcanfores.gateway.filtro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Log de acceso: {@code GET /api/catalogo/canchas -> 200 (35 ms) ip=190.1.2.3 id=...}. Se escribe cuando la
 * respuesta se confirma (así el estado es el real, incluso si lo puso un manejador de errores).
 */
@Component
public class FiltroRegistroAcceso implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FiltroRegistroAcceso.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long inicio = System.nanoTime();
        AtomicBoolean registrado = new AtomicBoolean(false);
        exchange.getResponse().beforeCommit(() -> {
            registrar(exchange, inicio, registrado, null);
            return Mono.empty();
        });
        return chain.filter(exchange).doFinally(senal -> {
            if (senal == SignalType.CANCEL) {
                registrar(exchange, inicio, registrado, "cancelada por el cliente");
            }
        });
    }

    private static void registrar(ServerWebExchange exchange, long inicio, AtomicBoolean registrado, String nota) {
        if (!registrado.compareAndSet(false, true)) {
            return;
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        HttpStatusCode estado = exchange.getResponse().getStatusCode();
        String metodo = exchange.getRequest().getMethod().name();
        String ruta = exchange.getRequest().getPath().value();
        String ip = IpCliente.de(exchange);
        String id = FiltroIdSolicitud.de(exchange);
        if (nota == null) {
            log.info("{} {} -> {} ({} ms) ip={} id={}", metodo, ruta, estado == null ? 200 : estado.value(), ms, ip, id);
        } else {
            log.info("{} {} -> {} ({} ms) ip={} id={}", metodo, ruta, nota, ms, ip, id);
        }
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.REGISTRO_ACCESO;
    }
}
