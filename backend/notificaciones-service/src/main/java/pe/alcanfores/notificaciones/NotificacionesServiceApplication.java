package pe.alcanfores.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * notificaciones-service: avisa al cliente por correo y WhatsApp (Evolution API) cada paso de su reserva,
 * adjunta la constancia y los comprobantes en PDF y envía el recordatorio 2 h antes del partido.
 */
// Thymeleaf: motores propios en PlantillasConfig (no hay vistas web). Usuarios en memoria: la autenticación es solo JWT.
@SpringBootApplication(exclude = {ThymeleafAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificacionesServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(NotificacionesServiceApplication.class, args);
    }
}
