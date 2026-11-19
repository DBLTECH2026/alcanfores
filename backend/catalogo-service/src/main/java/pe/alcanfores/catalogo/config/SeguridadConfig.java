package pe.alcanfores.catalogo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.SeguridadComun;

/**
 * Reglas de acceso (SPEC §2 y §5.2): el catálogo es público; el panel exige token (ADMIN o RECEPCION) y los métodos
 * que cambian datos son solo ADMIN ({@code @PreAuthorize}); {@code /interno/**} solo con la cabecera de servicio.
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
                .requestMatchers("/api/catalogo/**").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
                .anyRequest().denyAll());
        return http.build();
    }
}
