package pe.alcanfores.comun.seguridad;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración base de Spring Security que comparten todos los servicios. Uso en cada servicio:
 * <pre>{@code
 * @Bean SecurityFilterChain cadena(HttpSecurity http, ComponentesSeguridad seg) throws Exception {
 *     SeguridadComun.configurarBase(http, seg);
 *     http.authorizeHttpRequests(a -> a
 *         .requestMatchers(SeguridadComun.RUTAS_TECNICAS).permitAll()
 *         .requestMatchers("/interno/**").hasRole("INTERNO")
 *         .requestMatchers("/api/catalogo/**").permitAll()
 *         .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RECEPCION")
 *         .anyRequest().denyAll());
 *     return http.build();
 * }
 * }</pre>
 * Habilitar {@code @EnableMethodSecurity} en el servicio para usar {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
public final class SeguridadComun {

    /** Rutas técnicas siempre abiertas: salud, info, OpenAPI y la página de error. */
    public static final String[] RUTAS_TECNICAS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error"
    };

    private SeguridadComun() {
    }

    public static HttpSecurity configurarBase(HttpSecurity http, ComponentesSeguridad seg) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable) // CORS lo resuelve el API Gateway
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(seg.manejadores()).accessDeniedHandler(seg.manejadores()))
                .addFilterBefore(seg.internoFiltro(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(seg.jwtFiltro(), UsernamePasswordAuthenticationFilter.class);
    }
}
