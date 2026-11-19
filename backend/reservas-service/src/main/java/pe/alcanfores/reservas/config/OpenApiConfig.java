package pe.alcanfores.reservas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI (Swagger UI en {@code /swagger-ui.html}). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI documentacion() {
        return new OpenAPI()
                .info(new Info().title("Alcanfores · reservas-service").version("1.0.0")
                        .description("Turnos, disponibilidad, reservas, cancelaciones, asistencia, consulta RENIEC/SUNAT, "
                                + "constancia PDF, dashboard y reportes del Complejo Deportivo Los Alcanfores."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
