package pe.alcanfores.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.config.AlcanforesPropiedades;

import java.util.List;

/** Documentación OpenAPI en {@code /swagger-ui.html} con el esquema Bearer JWT. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documentacion(AlcanforesPropiedades propiedades) {
        return new OpenAPI()
                .info(new Info().title("Alcanfores · auth-service").version("1.0.0")
                        .description("Administradores, inicio de sesión con JWT, roles, bloqueo por intentos y bitácora central."))
                .servers(List.of(new Server().url(propiedades.getPublicApiUrl()).description("API Gateway")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
