package pe.alcanfores.pagos.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.seguridad.InterceptorInterno;

import java.time.Duration;

/** Clientes HTTP hacia otros microservicios: balanceados por Eureka, con token interno, timeout 3 s y circuit breaker. */
@Configuration
public class ClientesHttpConfig {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Builder para {@code http://<servicio>} (lo resuelve Spring Cloud LoadBalancer con Eureka). */
    @Bean
    @LoadBalanced
    RestClient.Builder clienteBalanceado(InterceptorInterno interceptor) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT);
        fabrica.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(fabrica).requestInterceptor(interceptor);
    }

    /** Builder normal (sin balanceo) para quien inyecte {@code RestClient.Builder} sin calificar. */
    @Bean
    @Primary
    @Scope("prototype")
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurador) {
        return configurador.configure(RestClient.builder());
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> configuracionCircuitos() {
        return fabrica -> fabrica.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(TIMEOUT.plusSeconds(1)).build())
                .build());
    }
}
