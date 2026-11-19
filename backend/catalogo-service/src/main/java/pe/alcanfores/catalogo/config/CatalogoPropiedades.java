package pe.alcanfores.catalogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades propias del catálogo ({@code alcanfores.catalogo.*}).
 *
 * @param uploadsDir   carpeta raíz de archivos subidos; las fotos van en {@code <uploadsDir>/canchas}
 * @param semillaFotos copiar al arrancar las fotos de {@code classpath:semilla/fotos} a las canchas sin fotos
 * @param reservasUrl  URL base de reservas-service (por Eureka: {@code http://reservas-service})
 */
@ConfigurationProperties(prefix = "alcanfores.catalogo")
public record CatalogoPropiedades(
        @DefaultValue("./uploads") String uploadsDir,
        @DefaultValue("true") boolean semillaFotos,
        @DefaultValue("http://reservas-service") String reservasUrl
) {
}
