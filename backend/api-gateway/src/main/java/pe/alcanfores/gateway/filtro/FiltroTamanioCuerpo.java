package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import pe.alcanfores.gateway.config.GatewayPropiedades;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tamaño máximo del cuerpo (6 MB). Si llega {@code Content-Length} se rechaza de inmediato con 413; si el cuerpo
 * viene por partes (sin longitud) se cuentan los bytes al vuelo y se corta al pasar el límite.
 */
@Component
public class FiltroTamanioCuerpo implements WebFilter, Ordered {

    public static final String CODIGO = "CUERPO_MUY_GRANDE";
    private final long maximo;
    private final String mensaje;
    private final RespuestaError error;

    public FiltroTamanioCuerpo(GatewayPropiedades propiedades, RespuestaError error) {
        this.maximo = propiedades.tamanioMaximoCuerpo().toBytes();
        this.mensaje = "Lo que enviaste supera el máximo de " + propiedades.tamanioMaximoCuerpo().toMegabytes()
                + " MB. Si es una imagen, redúcela e inténtalo de nuevo.";
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long declarado = exchange.getRequest().getHeaders().getContentLength();
        if (declarado > maximo) {
            return error.escribir(exchange, HttpStatus.PAYLOAD_TOO_LARGE, CODIGO, mensaje);
        }
        if (declarado >= 0) {
            return chain.filter(exchange);
        }
        ServerHttpRequest contado = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                AtomicLong leidos = new AtomicLong();
                return super.getBody().map(b -> {
                    if (leidos.addAndGet(b.readableByteCount()) > maximo) {
                        DataBufferUtils.release(b);
                        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, mensaje);
                    }
                    return b;
                });
            }
        };
        return chain.filter(exchange.mutate().request(contado).build());
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.TAMANIO_CUERPO;
    }
}
