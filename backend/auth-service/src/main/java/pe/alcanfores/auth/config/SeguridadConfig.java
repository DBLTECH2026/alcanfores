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
 * Reglas de acceso: login del panel, registro y login de clientes públicos; {@code /api/auth/clientes/**} solo con token
 * CLIENTE; el resto de {@code /api/auth/**} y {@code /api/admin/**} con rol ADMIN o RECEPCION (un token CLIENTE recibe
 * 403; los métodos solo-ADMIN llevan {@code @PreAuthorize}); todo lo demás cerrado.
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
                .requestMatchers(HttpMethod.POST, "/api/auth/clientes/registro", "/api/auth/clientes/login").permitAll()
                .requestMatchers("/api/auth/clientes/**").hasRole("CLIENTE")
                .requestMatchers("/api/auth/**").hasAnyRole("ADMIN", "RECEPCION")
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
