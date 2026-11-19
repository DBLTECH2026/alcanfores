package pe.alcanfores.notificaciones.cliente;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.function.Supplier;

/**
 * Ejecuta una llamada a otro microservicio dentro de su circuit breaker (Resilience4j). Si el servicio responde 404
 * se traduce a {@code NO_ENCONTRADO}; cualquier otra falla (caído, lento, circuito abierto) a 503
 * {@code SERVICIO_NO_DISPONIBLE} con el mensaje estándar del SPEC.
 */
@Component
@RequiredArgsConstructor
public class LlamadaProtegida {

    private static final Logger log = LoggerFactory.getLogger(LlamadaProtegida.class);
    private final CircuitBreakerFactory<?, ?> circuitBreakers;

    public <T> T ejecutar(String servicio, String nombreLegible, Supplier<T> llamada) {
        return circuitBreakers.create(servicio).run(llamada, error -> {
            throw traducir(servicio, nombreLegible, error);
        });
    }

    static NegocioExcepcion traducir(String servicio, String nombreLegible, Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof NegocioExcepcion ne) {
                return ne;
            }
            if (t instanceof HttpClientErrorException.NotFound) {
                return NegocioExcepcion.noEncontrado("El servicio de " + nombreLegible + " no encontró el recurso solicitado.");
            }
        }
        log.warn("Falla al llamar a {}: {}", servicio, error.toString());
        return NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de " + nombreLegible + " no responde. Intenta en unos segundos.");
    }
}
