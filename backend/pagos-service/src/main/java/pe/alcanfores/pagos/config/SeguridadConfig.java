package pe.alcanfores.pagos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.SeguridadComun;

/**
 * Rutas públicas del cliente (pagos, pasarela, webhooks, descarga de comprobantes con código + DNI), panel con token
 * (ADMIN o RECEPCION; las solo-ADMIN llevan {@code @PreAuthorize}) e internas con {@code X-Interno-Token}.
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
                .requestMatchers("/api/pagos/**", "/api/pasarela/**", "/api/webhooks/**", "/api/comprobantes/**").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
                .anyRequest().denyAll());
        return http.build();
    }
}
