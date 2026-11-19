package pe.alcanfores.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/** Microservicio de reservas del Complejo Deportivo Los Alcanfores (puerto 8093). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReservasServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(ReservasServiceApplication.class, args);
    }
}
