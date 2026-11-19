package pe.alcanfores.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Parámetros del gateway ({@code alcanfores.gateway.*}).
 *
 * @param tamanioMaximoCuerpo cuerpo máximo aceptado (6 MB: comprobante de 5 MB más los campos del formulario)
 * @param timeout             tiempo máximo de respuesta de una ruta normal
 * @param timeoutArchivosMs   tiempo máximo de las rutas de archivos (subidas, PDF, Excel)
 * @param timeoutDocumentosMs tiempo máximo de la consulta de DNI/RUC (Factiliza puede tardar ~11 s en un «no existe»)
 * @param servicios           servicios que muestra {@code /api/estado} (nombres de Eureka)
 * @param limites             reglas del limitador de solicitudes por IP
 */
@ConfigurationProperties(prefix = "alcanfores.gateway")
public record GatewayPropiedades(DataSize tamanioMaximoCuerpo, Duration timeout, long timeoutArchivosMs,
                                 long timeoutDocumentosMs,
                                 List<String> servicios, List<Limite> limites) {

    public GatewayPropiedades {
        tamanioMaximoCuerpo = tamanioMaximoCuerpo == null ? DataSize.ofMegabytes(6) : tamanioMaximoCuerpo;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        timeoutArchivosMs = timeoutArchivosMs <= 0 ? 20_000 : timeoutArchivosMs;
        timeoutDocumentosMs = timeoutDocumentosMs <= 0 ? 20_000 : timeoutDocumentosMs;
        servicios = servicios == null ? List.of() : List.copyOf(servicios);
        limites = limites == null ? List.of() : List.copyOf(limites);
    }

    /**
     * Regla de límite: {@code porMinuto} solicitudes por IP a las {@code rutas} (patrones) con el {@code metodo} dado
     * (vacío = cualquier método).
     */
    public record Limite(String nombre, String metodo, List<String> rutas, int porMinuto) {

        public Limite {
            rutas = rutas == null ? List.of() : List.copyOf(rutas);
        }
    }
}
