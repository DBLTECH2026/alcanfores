package pe.alcanfores.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import java.util.TimeZone;

/**
 * Registro de servicios (Eureka). Cada microservicio se anuncia aquí con su {@code spring.application.name} y el
 * API Gateway los encuentra por nombre ({@code lb://reservas-service}). Panel web en {@code http://localhost:8761}.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerAplicacion {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(DiscoveryServerAplicacion.class, args);
    }
}
