package pe.alcanfores.reservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias del servicio ({@code alcanfores.reservas.*}).
 *
 * @param tareasHabilitadas activa las tareas programadas (expiración, generación de turnos, refresco del catálogo)
 * @param semilla           datos de demostración
 * @param catalogoUrl       URL base de catalogo-service (por Eureka: {@code http://catalogo-service})
 * @param pagosUrl          URL base de pagos-service (por Eureka: {@code http://pagos-service})
 * @param factiliza         proveedor de consultas RENIEC/SUNAT
 */
@ConfigurationProperties(prefix = "alcanfores.reservas")
public record ReservasPropiedades(boolean tareasHabilitadas, Semilla semilla, String catalogoUrl, String pagosUrl,
                                  Factiliza factiliza) {

    public ReservasPropiedades {
        semilla = semilla == null ? new Semilla(false) : semilla;
        catalogoUrl = catalogoUrl == null ? "http://catalogo-service" : catalogoUrl;
        pagosUrl = pagosUrl == null ? "http://pagos-service" : pagosUrl;
        factiliza = factiliza == null ? new Factiliza(null, "https://api.factiliza.com/v1") : factiliza;
    }

    public record Semilla(boolean habilitada) {
    }

    public record Factiliza(String token, String url) {

        public boolean configurado() {
            return token != null && !token.isBlank();
        }
    }
}
