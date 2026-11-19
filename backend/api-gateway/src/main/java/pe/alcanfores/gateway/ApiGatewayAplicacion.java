package pe.alcanfores.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * API Gateway (Spring Cloud Gateway sobre WebFlux): única puerta de entrada del front. Enruta por nombre de servicio
 * vía Eureka, aplica CORS, valida el JWT en {@code /api/admin/**}, bloquea {@code /interno/**}, limita solicitudes,
 * corta el tamaño del cuerpo, protege cada ruta con circuit breaker y publica {@code /api/estado}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ApiGatewayAplicacion {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(ApiGatewayAplicacion.class, args);
    }
}
