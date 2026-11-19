package pe.alcanfores.notificaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Dos motores Thymeleaf independientes: HTML para los correos y TEXT para los mensajes de WhatsApp.
 * Se arman a mano (sin el starter web de Thymeleaf) porque el servicio no sirve vistas.
 */
@Configuration
public class PlantillasConfig {

    @Bean
    @Qualifier("motorCorreo")
    SpringTemplateEngine motorCorreo() {
        return motor("plantillas/correo/", ".html", TemplateMode.HTML);
    }

    @Bean
    @Qualifier("motorWhatsapp")
    SpringTemplateEngine motorWhatsapp() {
        return motor("plantillas/whatsapp/", ".txt", TemplateMode.TEXT);
    }

    /** Las columnas JSON (adjuntos, datos del recordatorio) usan el mismo ObjectMapper de Spring (fechas ISO). */
    @Bean
    HibernatePropertiesCustomizer jsonConObjectMapperDeSpring(ObjectMapper json) {
        return props -> props.put(AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(json));
    }

    private static SpringTemplateEngine motor(String prefijo, String sufijo, TemplateMode modo) {
        ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
        r.setPrefix(prefijo);
        r.setSuffix(sufijo);
        r.setTemplateMode(modo);
        r.setCharacterEncoding(StandardCharsets.UTF_8.name());
        r.setCacheable(true);
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(r);
        motor.setEnableSpringELCompiler(true);
        return motor;
    }
}
