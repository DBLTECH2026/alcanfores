package pe.alcanfores.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.gateway.filtro.FiltroCors;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * CORS: solo los orígenes de {@code FRONT_URL} (lista separada por comas, p. ej.
 * {@code https://alcanfores.pe,http://localhost:5186}). Sin cookies: el front usa {@code Authorization: Bearer}.
 */
@Configuration
public class CorsConfig {

    static final List<String> METODOS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    static final List<String> CABECERAS = List.of("Authorization", "Content-Type", "X-Request-Id");
    static final List<String> EXPUESTAS = List.of("X-Request-Id", "Content-Disposition");

    @Bean
    FiltroCors filtroCors(AlcanforesPropiedades propiedades) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origenes(propiedades.getFrontUrl()));
        c.setAllowedMethods(METODOS);
        c.setAllowedHeaders(CABECERAS);
        c.setExposedHeaders(EXPUESTAS);
        c.setAllowCredentials(false);
        c.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", c);
        return new FiltroCors(origen);
    }

    /** «https://a.pe/, http://localhost:5186» → [https://a.pe, http://localhost:5186] */
    static List<String> origenes(String frontUrl) {
        if (frontUrl == null || frontUrl.isBlank()) {
            return List.of();
        }
        return Arrays.stream(frontUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .distinct()
                .toList();
    }
}
