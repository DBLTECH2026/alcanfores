package pe.alcanfores.comun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.ManejadorErroresInfraestructura;
import pe.alcanfores.comun.error.ManejadorGlobalErrores;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.FiltroIdSolicitud;
import pe.alcanfores.comun.seguridad.InterceptorInterno;
import pe.alcanfores.comun.seguridad.InternoFiltro;
import pe.alcanfores.comun.seguridad.JwtFiltroAutenticacion;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.SeguridadComun;
import pe.alcanfores.comun.seguridad.UsuarioToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Qué beans registra la autoconfiguración de {@code comun} según el tipo de aplicación y el classpath. */
class ComunAutoConfiguracionTest {

    private static final String SECRETO = "secreto-de-prueba-de-la-autoconfiguracion-32b";

    @Configuration(proxyBeanMethods = false)
    static class Infraestructura {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }
    }

    private final ApplicationContextRunner noWeb = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ComunAutoConfiguracion.class))
            .withUserConfiguration(Infraestructura.class);

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ComunAutoConfiguracion.class))
            .withUserConfiguration(Infraestructura.class);

    @Test
    void propiedadesPorDefecto() {
        AlcanforesPropiedades p = new AlcanforesPropiedades();
        assertThat(p.getJwt().getHoras()).isEqualTo(8);
        assertThat(p.getJwt().getSecreto()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(p.getInternoToken()).isNotBlank();
        assertThat(p.getFrontUrl()).isEqualTo("http://localhost:5186");
        assertThat(p.getPublicApiUrl()).isEqualTo("http://localhost:8086");
    }

    @Test
    void aplicacionNoWebSoloTieneJwtYMensajeria() {
        noWeb.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(JwtServicio.class).hasSingleBean(PublicadorEventos.class)
                    .hasSingleBean(AlcanforesPropiedades.class);
            assertThat(ctx).doesNotHaveBean(ManejadorGlobalErrores.class)
                    .doesNotHaveBean(InterceptorInterno.class)
                    .doesNotHaveBean(ComponentesSeguridad.class)
                    .doesNotHaveBean(Auditor.class); // sin solicitud HTTP no hay auditoría
            assertThat(ctx.getBean(JwtServicio.class).getHoras()).isEqualTo(8);
        });
    }

    @Test
    void mensajeriaDeclaraExchangesColaMuertaYConvertidorJson() {
        noWeb.run(ctx -> {
            assertThat(ctx.getBeansOfType(TopicExchange.class).values()).extracting(TopicExchange::getName)
                    .containsExactlyInAnyOrder(Eventos.EXCHANGE, Eventos.DLX);
            assertThat(ctx.getBeansOfType(TopicExchange.class).values()).allMatch(x -> x.isDurable() && !x.isAutoDelete());
            Queue muertos = ctx.getBean("colaMuertos", Queue.class);
            assertThat(muertos.getName()).isEqualTo(Eventos.COLA_MUERTA);
            assertThat(muertos.isDurable()).isTrue();
            Binding enlace = ctx.getBean(Binding.class);
            assertThat(enlace.getExchange()).isEqualTo(Eventos.DLX);
            assertThat(enlace.getDestination()).isEqualTo(Eventos.COLA_MUERTA);
            assertThat(enlace.getRoutingKey()).isEqualTo("#");
            assertThat(ctx.getBean(MessageConverter.class)).isInstanceOf(Jackson2JsonMessageConverter.class);
        });
    }

    @Test
    void sinRabbitEnElClasspathNoHayMensajeria() {
        noWeb.withClassLoader(new FilteredClassLoader(RabbitTemplate.class)).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(JwtServicio.class);
            assertThat(ctx).doesNotHaveBean(PublicadorEventos.class).doesNotHaveBean(TopicExchange.class);
        });
    }

    @Test
    void propiedadesAlcanforesSeAplican() {
        noWeb.withPropertyValues("alcanfores.jwt.secreto=" + SECRETO, "alcanfores.jwt.horas=2",
                "alcanfores.interno-token=tok", "alcanfores.front-url=https://alcanfores.pe",
                "alcanfores.public-api-url=https://api.alcanfores.pe").run(ctx -> {
            AlcanforesPropiedades p = ctx.getBean(AlcanforesPropiedades.class);
            assertThat(p.getJwt().getSecreto()).isEqualTo(SECRETO);
            assertThat(p.getInternoToken()).isEqualTo("tok");
            assertThat(p.getFrontUrl()).isEqualTo("https://alcanfores.pe");
            assertThat(p.getPublicApiUrl()).isEqualTo("https://api.alcanfores.pe");
            JwtServicio jwt = ctx.getBean(JwtServicio.class);
            assertThat(jwt.getHoras()).isEqualTo(2);
            // Firma con el secreto configurado: otro JwtServicio con ese secreto valida el token.
            String token = jwt.generar(new UsuarioToken(1L, "a", "A", "ADMIN"));
            assertThat(new JwtServicio(SECRETO, 1).validar(token).id()).isEqualTo(1L);
        });
    }

    @Test
    void secretoJwtCortoHaceFallarElArranque() {
        noWeb.withPropertyValues("alcanfores.jwt.secreto=corto").run(ctx ->
                assertThat(ctx).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void unJwtServicioPropioReemplazaAlDeLaLibreria() {
        JwtServicio propio = new JwtServicio(SECRETO, 1);
        noWeb.withBean(JwtServicio.class, () -> propio).run(ctx ->
                assertThat(ctx.getBean(JwtServicio.class)).isSameAs(propio));
    }

    @Test
    void aplicacionServletRecibeErroresFiltrosSeguridadYAuditoria() {
        web.withPropertyValues("spring.application.name=reservas-service", "alcanfores.interno-token=tok").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(ManejadorGlobalErrores.class)
                    .hasSingleBean(ManejadorErroresInfraestructura.class)
                    .hasSingleBean(InterceptorInterno.class)
                    .hasSingleBean(ComponentesSeguridad.class)
                    .hasSingleBean(Auditor.class)
                    .hasSingleBean(FilterRegistrationBean.class);

            FilterRegistrationBean<?> registro = ctx.getBean(FilterRegistrationBean.class);
            assertThat(registro.getFilter()).isInstanceOf(FiltroIdSolicitud.class);
            assertThat(registro.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);

            ComponentesSeguridad seg = ctx.getBean(ComponentesSeguridad.class);
            assertThat(seg.jwtFiltro()).isNotNull();
            assertThat(seg.internoFiltro()).isNotNull();
            assertThat(seg.manejadores()).isNotNull();
        });
    }

    @Test
    void sinSpringSecurityNoSeRegistranLosComponentesDeSeguridad() {
        web.withClassLoader(new FilteredClassLoader(SecurityFilterChain.class)).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ManejadorGlobalErrores.class);
            assertThat(ctx).doesNotHaveBean(ComponentesSeguridad.class)
                    .doesNotHaveBean(ManejadorErroresInfraestructura.class);
        });
    }

    // ---------- SeguridadComun.configurarBase con un SecurityFilterChain real ----------

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class ConfigSeguridad {
        @Bean
        SecurityFilterChain cadena(HttpSecurity http, ComponentesSeguridad seg) throws Exception {
            SeguridadComun.configurarBase(http, seg);
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
            return http.build();
        }
    }

    @Test
    void configurarBaseAgregaLosFiltrosJwtEInternoYQuitaCsrf() {
        web.withUserConfiguration(ConfigSeguridad.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            List<Filter> filtros = ctx.getBean(SecurityFilterChain.class).getFilters();
            assertThat(filtros).hasAtLeastOneElementOfType(JwtFiltroAutenticacion.class)
                    .hasAtLeastOneElementOfType(InternoFiltro.class)
                    .noneMatch(CsrfFilter.class::isInstance);
            int interno = indiceDe(filtros, InternoFiltro.class);
            int jwt = indiceDe(filtros, JwtFiltroAutenticacion.class);
            assertThat(interno).isLessThan(jwt);
        });
    }

    private static int indiceDe(List<Filter> filtros, Class<?> tipo) {
        for (int i = 0; i < filtros.size(); i++) {
            if (tipo.isInstance(filtros.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
