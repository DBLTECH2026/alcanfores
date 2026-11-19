package pe.alcanfores.reservas.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.alcanfores.comun.util.Horarios;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Horas en formato {@code HH:mm} (SPEC §0). Al leer se acepta {@code HH:mm} o {@code HH:mm:ss} y también {@code 24:00}
 * (cierre a medianoche, como lo puede mandar catálogo o el panel), que se guarda como 23:59:59. Al escribir, el fin del día
 * sale {@code 23:59} (los demás servicios lo leen sin cambios).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer horasCortas() {
        return b -> b.serializerByType(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")))
                .deserializerByType(LocalTime.class, new HoraConMedianoche());
    }

    /** {@code "24:00"} → 23:59:59; lo demás, como Jackson (ISO). */
    static class HoraConMedianoche extends LocalTimeDeserializer {
        HoraConMedianoche() {
            super(DateTimeFormatter.ISO_LOCAL_TIME);
        }

        @Override
        public LocalTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            if (p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING) && p.getText().trim().startsWith("24:00")) {
                return Horarios.FIN_DEL_DIA;
            }
            return super.deserialize(p, ctx);
        }
    }
}
