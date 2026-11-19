package pe.alcanfores.pagos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI documentacionPagos() {
        return new OpenAPI()
                .info(new Info().title("Alcanfores · pagos-service").version("1.0.0")
                        .description("Pagos con comprobante Yape/Plin, caja, Pasarela Alcanfores Pay (beta), Mercado Pago "
                                + "(sandbox) y comprobantes electrónicos SIMULADOS (sin validez tributaria)."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
