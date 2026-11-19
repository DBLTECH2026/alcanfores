package pe.alcanfores.reservas.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Horas en formato {@code HH:mm} (SPEC §0). Al leer se acepta {@code HH:mm} o {@code HH:mm:ss}. */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer horasCortas() {
        return b -> b.serializerByType(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
    }
}
