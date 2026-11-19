package pe.alcanfores.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/** catalogo-service · canchas, fotos, reseñas verificadas y configuración del complejo (puerto 8092). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CatalogoAplicacion {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(CatalogoAplicacion.class, args);
    }
}
