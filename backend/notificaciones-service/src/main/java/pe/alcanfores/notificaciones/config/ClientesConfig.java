package pe.alcanfores.notificaciones.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.seguridad.InterceptorInterno;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;

import java.time.Duration;

/**
 * Clientes HTTP. Hacia otros microservicios: {@code RestClient} balanceado por Eureka ({@code http://reservas-service})
 * con {@code X-Interno-Token}. Hacia Evolution API (externo): {@code RestClient} sin balanceo.
 */
@Configuration
public class ClientesConfig {

    public static final Duration CONEXION = Duration.ofSeconds(3);
    public static final Duration LECTURA_CONSULTAS = Duration.ofSeconds(3);
    /** Los PDF los genera el otro servicio al vuelo (openhtmltopdf): se le da algo más de margen para leer. */
    public static final Duration LECTURA_DOCUMENTOS = Duration.ofSeconds(10);

    @Bean
    @LoadBalanced
    RestClient.Builder clienteBalanceado(InterceptorInterno interceptorInterno) {
        return RestClient.builder().requestInterceptor(interceptorInterno);
    }

    /** Consultas cortas (configuración del complejo, datos de la reserva). */
    @Bean
    @Qualifier("restInterno")
    RestClient restInterno(@LoadBalanced RestClient.Builder clienteBalanceado) {
        return clienteBalanceado.clone().requestFactory(fabrica(CONEXION, LECTURA_CONSULTAS)).build();
    }

    /** Descarga de PDFs (constancia y comprobantes). */
    @Bean
    @Qualifier("restDocumentos")
    RestClient restDocumentos(@LoadBalanced RestClient.Builder clienteBalanceado) {
        return clienteBalanceado.clone().requestFactory(fabrica(CONEXION, LECTURA_DOCUMENTOS)).build();
    }

    @Bean
    EvolutionCliente evolutionCliente(NotificacionesPropiedades propiedades) {
        Duration lectura = Duration.ofSeconds(propiedades.getEvolution().getTimeoutSegundos());
        return new EvolutionCliente(RestClient.builder().requestFactory(fabrica(CONEXION, lectura)), propiedades);
    }

    /**
     * Circuit breaker por defecto: abre al 50 % de fallos en una ventana de 10 llamadas; el límite de tiempo
     * lo ponen los timeouts del RestClient (el TimeLimiter queda holgado para no cortar antes).
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerPorDefecto() {
        return fabrica -> fabrica.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(15)).build())
                .build());
    }

    static SimpleClientHttpRequestFactory fabrica(Duration conexion, Duration lectura) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(conexion);
        f.setReadTimeout(lectura);
        return f;
    }
}
