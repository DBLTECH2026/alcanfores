package pe.alcanfores.gateway.controlador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import pe.alcanfores.gateway.filtro.FiltroIdSolicitud;
import pe.alcanfores.gateway.filtro.FiltroTamanioCuerpo;
import pe.alcanfores.gateway.filtro.RespuestaError;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Destino del circuit breaker ({@code forward:/fallback/<servicio>}): 503 {@code SERVICIO_NO_DISPONIBLE} con un
 * mensaje amable. No se publica en Swagger porque nadie lo llama directo.
 */
@RestController
public class FallbackControlador {

    private static final Logger log = LoggerFactory.getLogger(FallbackControlador.class);

    /** Nombre que entiende la persona que usa el sistema. */
    static final Map<String, String> NOMBRES = Map.of(
            "auth", "acceso",
            "catalogo", "catálogo",
            "reservas", "reservas",
            "pagos", "pagos",
            "notificaciones", "notificaciones",
            "asistente", "Canchita (asistente)");

    private final RespuestaError error;

    public FallbackControlador(RespuestaError error) {
        this.error = error;
    }

    @RequestMapping("/fallback/{servicio}")
    public Mono<Void> fallback(@PathVariable String servicio, ServerWebExchange exchange) {
        Throwable causa = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        if (causa instanceof ResponseStatusException rse && rse.getStatusCode().value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return error.escribir(exchange, HttpStatus.PAYLOAD_TOO_LARGE, FiltroTamanioCuerpo.CODIGO, rse.getReason());
        }
        String nombre = NOMBRES.getOrDefault(servicio, servicio);
        log.warn("Fallback de {}: {} (id={})", servicio, causa == null ? "circuito abierto" : causa.toString(),
                FiltroIdSolicitud.de(exchange));
        return error.escribir(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
                "El servicio de " + nombre + " no responde. Intenta en unos segundos.");
    }
}
