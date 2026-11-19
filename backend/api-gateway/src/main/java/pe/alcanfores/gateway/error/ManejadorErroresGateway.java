package pe.alcanfores.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import pe.alcanfores.gateway.filtro.FiltroIdSolicitud;
import pe.alcanfores.gateway.filtro.FiltroTamanioCuerpo;
import pe.alcanfores.gateway.filtro.RespuestaError;
import reactor.core.publisher.Mono;

/**
 * Todo error del propio gateway (ruta inexistente, método no permitido, servicio sin instancias…) sale con el
 * cuerpo estándar {@code ErrorApi} y en español, en lugar del JSON por defecto de Spring.
 */
@Component
@Order(-2)
public class ManejadorErroresGateway implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGateway.class);
    private final RespuestaError error;

    public ManejadorErroresGateway(RespuestaError error) {
        this.error = error;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            return responder(exchange, status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, rse);
        }
        log.error("Error no controlado en el gateway ({} {}, id={})", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(), FiltroIdSolicitud.de(exchange), ex);
        return error.escribir(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Intenta de nuevo en unos segundos.");
    }

    private Mono<Void> responder(ServerWebExchange exchange, HttpStatus status, ResponseStatusException ex) {
        return switch (status) {
            case NOT_FOUND -> error.escribir(exchange, status, "NO_ENCONTRADO", "La ruta solicitada no existe.");
            case METHOD_NOT_ALLOWED -> error.escribir(exchange, status, "METODO_NO_PERMITIDO",
                    "Método HTTP no permitido en esta ruta.");
            case UNSUPPORTED_MEDIA_TYPE -> error.escribir(exchange, status, "TIPO_NO_SOPORTADO", "Tipo de contenido no soportado.");
            case PAYLOAD_TOO_LARGE -> error.escribir(exchange, status, FiltroTamanioCuerpo.CODIGO,
                    ex.getReason() == null ? "Lo que enviaste es demasiado grande." : ex.getReason());
            case SERVICE_UNAVAILABLE, BAD_GATEWAY -> error.escribir(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICIO_NO_DISPONIBLE", "El servicio no responde. Intenta en unos segundos.");
            case GATEWAY_TIMEOUT -> error.escribir(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
                    "El servicio tardó demasiado en responder. Intenta en unos segundos.");
            case BAD_REQUEST -> error.escribir(exchange, status, "SOLICITUD_INVALIDA", "La solicitud no tiene un formato válido.");
            default -> error.escribir(exchange, status, status.name(), "No se pudo atender la solicitud (" + status.value() + ").");
        };
    }
}
