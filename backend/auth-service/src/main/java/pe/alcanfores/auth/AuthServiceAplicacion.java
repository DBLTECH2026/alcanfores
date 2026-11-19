package pe.alcanfores.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * auth-service: administradores, login JWT, roles, bloqueo por intentos, cambio de contraseña y bitácora central.
 * Sin el usuario «user» que Spring Security crea por defecto: la autenticación es solo por JWT.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AuthServiceAplicacion {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(AuthServiceAplicacion.class, args);
    }
}
