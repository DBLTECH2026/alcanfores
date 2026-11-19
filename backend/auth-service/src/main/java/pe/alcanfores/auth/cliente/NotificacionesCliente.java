package pe.alcanfores.auth.cliente;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.Canal;
import pe.alcanfores.comun.error.NegocioExcepcion;

/**
 * Envía, de forma síncrona, el código para cambiar la contraseña (o el aviso de que se cambió) por notificaciones-service:
 * {@code POST http://notificaciones-service/interno/codigos}. Si el servicio no responde → 503 {@code SERVICIO_NO_DISPONIBLE}.
 */
@Component
public class NotificacionesCliente {

    static final String URL = "http://notificaciones-service/interno/codigos";
    public static final String RECUPERAR = "RECUPERAR_CONTRASENA";
    public static final String CAMBIADA = "CONTRASENA_CAMBIADA";
    private static final Logger log = LoggerFactory.getLogger(NotificacionesCliente.class);

    private final RestClient rest;

    public NotificacionesCliente(@Qualifier("restNotificaciones") RestClient rest) {
        this.rest = rest;
    }

    /** Cuerpo de {@code /interno/codigos}; {@code codigo} es nulo en el aviso de contraseña cambiada. */
    public record Solicitud(Canal canal, String destino, String nombre, String codigo, String motivo) {

        @Override
        public String toString() {
            return "Solicitud[" + canal + ", " + motivo + ", codigo=******]";
        }
    }

    /** {@code estado}: ENVIADO, SIMULADO (canal sin configurar) o FALLIDO. */
    public record Resultado(String estado, boolean simulado, String detalle) {

        public boolean fallido() {
            return "FALLIDO".equals(estado);
        }
    }

    public Resultado enviar(Solicitud s) {
        try {
            Resultado r = rest.post().uri(URL).contentType(MediaType.APPLICATION_JSON).body(s).retrieve().body(Resultado.class);
            if (r == null) {
                throw new RestClientException("respuesta vacía");
            }
            return r;
        } catch (RestClientException e) {
            log.warn("notificaciones-service no respondió al enviar {} por {}: {}", s.motivo(), s.canal(), e.getMessage());
            throw NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                    "El servicio de notificaciones no responde. Intenta en unos segundos.");
        }
    }
}
