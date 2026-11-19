package pe.alcanfores.notificaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.SeguridadComun;

/**
 * Este servicio no tiene rutas públicas: todo lo de {@code /api/admin/**} pide token (ADMIN o RECEPCION) y las
 * acciones solo-ADMIN se marcan con {@code @PreAuthorize} en el controlador (SPEC §2 y §5.5).
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
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
                .anyRequest().denyAll());
        return http.build();
    }
}
