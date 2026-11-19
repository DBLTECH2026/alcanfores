package pe.alcanfores.gateway.filtro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pe.alcanfores.comun.error.ErrorApi;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Escribe el cuerpo de error estándar de {@code comun} ({@link ErrorApi}) desde filtros y manejadores reactivos. */
@Component
public class RespuestaError {

    private final ObjectMapper json;

    public RespuestaError(ObjectMapper json) {
        this.json = json;
    }

    public Mono<Void> escribir(ServerWebExchange exchange, HttpStatus status, String codigo, String mensaje) {
        ServerHttpResponse res = exchange.getResponse();
        if (res.isCommitted()) {
            return Mono.empty();
        }
        ErrorApi error = ErrorApi.de(status.value(), codigo, mensaje, rutaOriginal(exchange));
        byte[] cuerpo;
        try {
            cuerpo = json.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            cuerpo = ("{\"status\":" + status.value() + ",\"codigo\":\"" + codigo + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        res.setStatusCode(status);
        res.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        res.getHeaders().setContentLength(cuerpo.length);
        DataBuffer buffer = res.bufferFactory().wrap(cuerpo);
        return res.writeWith(Mono.just(buffer));
    }

    /** En un fallback ({@code forward:/fallback/x}) la ruta que interesa es la que pidió el cliente, no la interna. */
    static String rutaOriginal(ServerWebExchange exchange) {
        Set<URI> originales = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originales != null && !originales.isEmpty()) {
            return originales.iterator().next().getRawPath();
        }
        return exchange.getRequest().getPath().value();
    }
}
