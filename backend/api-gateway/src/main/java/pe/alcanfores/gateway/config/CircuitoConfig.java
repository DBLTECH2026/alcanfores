package pe.alcanfores.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker de cada ruta (Resilience4j). Se abre si en las últimas 20 llamadas (mínimo 10) falla la mitad o
 * más; abierto 15 s, luego deja pasar 3 de prueba. Mientras está abierto, o si el servicio no responde a tiempo, la
 * ruta responde el fallback 503 al instante. Las rutas «-archivos» tienen más tiempo (subidas, PDF, Excel) y
 * «-documentos» también (consulta RENIEC/SUNAT); «asistente» (Canchita, IA) tiene 60 s.
 */
@Configuration
public class CircuitoConfig {

    /** Margen sobre el timeout HTTP para que primero corte el cliente HTTP del gateway (504) y no el limitador. */
    static final Duration MARGEN = Duration.ofSeconds(2);

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> configuracionCircuitBreakers(GatewayPropiedades p) {
        CircuitBreakerConfig circuito = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return fabrica -> fabrica.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuito)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(limite(id, p)).build())
                .build());
    }

    static Duration limite(String idCircuito, GatewayPropiedades p) {
        Duration base;
        if (idCircuito.endsWith("-archivos")) {
            base = Duration.ofMillis(p.timeoutArchivosMs());
        } else if (idCircuito.endsWith("-documentos")) {
            base = Duration.ofMillis(p.timeoutDocumentosMs());
        } else if (idCircuito.equals("asistente")) {
            base = Duration.ofMillis(p.timeoutAsistenteMs());
        } else {
            base = p.timeout();
        }
        return base.plus(MARGEN);
    }
}
