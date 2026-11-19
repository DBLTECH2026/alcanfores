package pe.alcanfores.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.seguridad.InterceptorInterno;

import java.time.Duration;

/**
 * Cliente HTTP hacia notificaciones-service, balanceado por Eureka ({@code http://notificaciones-service}) y con
 * {@code X-Interno-Token}. La lectura espera hasta 18 s porque el envío del código por WhatsApp es síncrono (el
 * gateway le da 20 s a {@code /api/auth/recuperar/**}).
 */
@Configuration
public class ClientesConfig {

    static final Duration CONEXION = Duration.ofSeconds(3);
    static final Duration LECTURA = Duration.ofSeconds(18);

    @Bean
    @LoadBalanced
    RestClient.Builder clienteBalanceado(InterceptorInterno interceptorInterno) {
        return RestClient.builder().requestInterceptor(interceptorInterno);
    }

    @Bean
    @Qualifier("restNotificaciones")
    RestClient restNotificaciones(@LoadBalanced RestClient.Builder clienteBalanceado) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(CONEXION);
        f.setReadTimeout(LECTURA);
        return clienteBalanceado.clone().requestFactory(f).build();
    }
}
