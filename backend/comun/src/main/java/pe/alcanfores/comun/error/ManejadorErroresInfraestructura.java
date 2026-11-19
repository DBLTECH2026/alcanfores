package pe.alcanfores.comun.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Errores de seguridad y de base de datos. Se registra solo si Spring Security y Spring DAO están en el classpath
 * (todos los servicios de dominio los tienen) y va antes que {@link ManejadorGlobalErrores}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErroresInfraestructura {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresInfraestructura.class);

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorApi> sinPermiso(RuntimeException e, HttpServletRequest req) {
        return ManejadorGlobalErrores.responder(403, "SIN_PERMISO", "Tu rol no tiene permiso para esta acción.", req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorApi> noAutenticado(AuthenticationException e, HttpServletRequest req) {
        return ManejadorGlobalErrores.responder(401, "NO_AUTENTICADO", "Inicia sesión para continuar.", req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorApi> integridad(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return ManejadorGlobalErrores.responder(409, "CONFLICTO_DATOS",
                "Los datos chocan con un registro existente (por ejemplo, un turno ya reservado o un valor repetido).", req, List.of());
    }

    /** {@code ?sort=campoInexistente} en un listado paginado. */
    @ExceptionHandler(org.springframework.data.mapping.PropertyReferenceException.class)
    public ResponseEntity<ErrorApi> ordenInvalido(org.springframework.data.mapping.PropertyReferenceException e,
                                                  HttpServletRequest req) {
        String mensaje = "No se puede ordenar por «" + e.getPropertyName() + "».";
        return ManejadorGlobalErrores.responder(400, "VALIDACION", mensaje, req, List.of(new ErrorCampo("sort", mensaje)));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorApi> concurrencia(RuntimeException e, HttpServletRequest req) {
        return ManejadorGlobalErrores.responder(409, "CONFLICTO_CONCURRENCIA",
                "Otra persona modificó este registro al mismo tiempo. Vuelve a intentarlo.", req, List.of());
    }
}
