package pe.alcanfores.reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.SeguridadComun;

/**
 * Reglas de acceso (SPEC §2): portal público abierto, {@code /api/admin/**} con token (ADMIN o RECEPCION; lo exclusivo
 * de ADMIN se marca con {@code @PreAuthorize}) y {@code /interno/**} solo con {@code X-Interno-Token}.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, ComponentesSeguridad seg) throws Exception {
        SeguridadComun.configurarBase(http, seg);
        http.authorizeHttpRequests(a -> a
                .requestMatchers(SeguridadComun.RUTAS_TECNICAS).permitAll()
                .requestMatchers("/interno/**").hasRole("INTERNO")
                .requestMatchers(HttpMethod.GET, "/api/disponibilidad", "/api/disponibilidad/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/documentos/**").permitAll()
                .requestMatchers("/api/reservas", "/api/reservas/**").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
                .anyRequest().denyAll());
        return http.build();
    }
}
