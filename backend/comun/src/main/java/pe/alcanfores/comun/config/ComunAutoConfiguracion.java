package pe.alcanfores.comun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;
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
import pe.alcanfores.comun.seguridad.ManejadoresSeguridad;

/**
 * Autoconfiguración de la librería común: cada microservicio solo agrega la dependencia {@code comun}
 * y recibe JwtServicio, manejo de errores, filtros de seguridad, bus de eventos y auditoría.
 */
@AutoConfiguration
@EnableConfigurationProperties(AlcanforesPropiedades.class)
public class ComunAutoConfiguracion {

    @Bean
    @ConditionalOnMissingBean
    public JwtServicio jwtServicio(AlcanforesPropiedades p) {
        return new JwtServicio(p.getJwt().getSecreto(), p.getJwt().getHoras());
    }

    /** Servicios Servlet (todos menos el gateway, que es WebFlux). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    static class Web {

        @Bean
        ManejadorGlobalErrores manejadorGlobalErrores() {
            return new ManejadorGlobalErrores();
        }

        @Bean
        InterceptorInterno interceptorInterno(AlcanforesPropiedades p) {
            return new InterceptorInterno(p.getInternoToken());
        }

        @Bean
        FilterRegistrationBean<FiltroIdSolicitud> filtroIdSolicitud() {
            FilterRegistrationBean<FiltroIdSolicitud> r = new FilterRegistrationBean<>(new FiltroIdSolicitud());
            r.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return r;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    @ConditionalOnClass({SecurityFilterChain.class, org.springframework.dao.DataAccessException.class})
    static class Seguridad {

        @Bean
        ManejadorErroresInfraestructura manejadorErroresInfraestructura() {
            return new ManejadorErroresInfraestructura();
        }

        @Bean
        ComponentesSeguridad componentesSeguridad(JwtServicio jwt, AlcanforesPropiedades p, ObjectMapper json) {
            return new ComponentesSeguridad(new JwtFiltroAutenticacion(jwt), new InternoFiltro(p.getInternoToken(), json),
                    new ManejadoresSeguridad(json));
        }
    }

    /** Bus de eventos con RabbitMQ (exchange topic + DLX + JSON). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate.class)
    static class Mensajeria {

        @Bean
        TopicExchange intercambioEventos() {
            return new TopicExchange(Eventos.EXCHANGE, true, false);
        }

        @Bean
        TopicExchange intercambioMuertos() {
            return new TopicExchange(Eventos.DLX, true, false);
        }

        @Bean
        Queue colaMuertos() {
            return QueueBuilder.durable(Eventos.COLA_MUERTA).build();
        }

        @Bean
        Binding enlaceMuertos(Queue colaMuertos, TopicExchange intercambioMuertos) {
            return BindingBuilder.bind(colaMuertos).to(intercambioMuertos).with("#");
        }

        @Bean
        MessageConverter convertidorJson(ObjectMapper json) {
            Jackson2JsonMessageConverter c = new Jackson2JsonMessageConverter(json);
            DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
            mapper.setTrustedPackages("pe.alcanfores.comun.eventos");
            c.setJavaTypeMapper(mapper);
            return c;
        }

        // RabbitTemplate y la fábrica de listeners los crea Spring Boot y toman este MessageConverter automáticamente.

        @Bean
        PublicadorEventos publicadorEventos(RabbitTemplate rabbit) {
            return new PublicadorEventos(rabbit);
        }

        @Bean
        @ConditionalOnWebApplication(type = Type.SERVLET)
        Auditor auditor(PublicadorEventos publicador, @Value("${spring.application.name:servicio}") String servicio) {
            return new Auditor(publicador, servicio);
        }
    }
}
