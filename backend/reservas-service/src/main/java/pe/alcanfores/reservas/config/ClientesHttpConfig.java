package pe.alcanfores.reservas.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.seguridad.InterceptorInterno;
import pe.alcanfores.reservas.cliente.FactilizaProveedor;
import pe.alcanfores.reservas.cliente.ProveedorDocumentos;

import java.time.Duration;

/** Clientes HTTP: uno balanceado por Eureka para otros microservicios y uno directo para proveedores externos. */
@Configuration(proxyBeanMethods = false)
public class ClientesHttpConfig {

    private static final Duration TIMEOUT_INTERNO = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_FACTILIZA = Duration.ofSeconds(5);

    /** Builder por defecto (sin balanceo): lo usan Eureka y cualquier otra librería que pida un RestClient.Builder. */
    @Bean
    @Primary
    RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurador) {
        return configurador.configure(RestClient.builder());
    }

    /** {@code http://<servicio>} resuelto por Eureka, con X-Interno-Token, X-Request-Id y timeout de 3 s. */
    @Bean
    @LoadBalanced
    RestClient.Builder clienteBalanceado(RestClientBuilderConfigurer configurador, InterceptorInterno interceptor) {
        return configurador.configure(RestClient.builder())
                .requestFactory(fabrica(TIMEOUT_INTERNO))
                .requestInterceptor(interceptor);
    }

    @Bean
    ProveedorDocumentos proveedorDocumentos(RestClientBuilderConfigurer configurador, ReservasPropiedades propiedades) {
        return new FactilizaProveedor(configurador.configure(RestClient.builder()).requestFactory(fabrica(TIMEOUT_FACTILIZA)),
                propiedades.factiliza());
    }

    private static SimpleClientHttpRequestFactory fabrica(Duration timeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeout);
        f.setReadTimeout(timeout);
        return f;
    }
}
