package pe.alcanfores.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.SeguridadComun;

/**
 * Reglas de acceso: login público; {@code /api/auth/**} con token; {@code /api/admin/**} con rol ADMIN o RECEPCION
 * (los métodos solo-ADMIN llevan {@code @PreAuthorize}); todo lo demás cerrado.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, ComponentesSeguridad seg) throws Exception {
        SeguridadComun.configurarBase(http, seg);
        http.authorizeHttpRequests(a -> a
                .requestMatchers(SeguridadComun.RUTAS_TECNICAS).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
                .requestMatchers("/interno/**").hasRole("INTERNO")
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder codificadorContrasenas(AuthPropiedades propiedades) {
        return new BCryptPasswordEncoder(propiedades.bcryptFuerza());
    }
}
