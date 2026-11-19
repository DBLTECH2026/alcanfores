package pe.alcanfores.catalogo.config;

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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.seguridad.InterceptorInterno;

import java.time.Duration;

/**
 * Clientes HTTP hacia otros microservicios: {@code RestClient} balanceado por Eureka, con la cabecera
 * {@code X-Interno-Token}, timeout de 3 s y circuit breaker de Resilience4j.
 */
@Configuration
public class ClientesConfig {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Builder común (sin balanceo) para cualquier otro uso, así nadie recibe por error el balanceado. */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    @LoadBalanced
    RestClient.Builder clienteBalanceado(RestClientBuilderConfigurer configurer, InterceptorInterno interceptorInterno) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT);
        fabrica.setReadTimeout(TIMEOUT);
        return configurer.configure(RestClient.builder()).requestFactory(fabrica).requestInterceptor(interceptorInterno);
    }

    @Bean
    RestClient clienteReservas(@LoadBalanced RestClient.Builder clienteBalanceado, CatalogoPropiedades propiedades) {
        return clienteBalanceado.clone().baseUrl(propiedades.reservasUrl()).build();
    }

    /** Abre el circuito si falla la mitad de las últimas 10 llamadas; lo vuelve a probar a los 20 s. */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> configuracionCircuitos() {
        return fabrica -> fabrica.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(TIMEOUT.plusSeconds(1)).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build())
                .build());
    }
}
