package pe.alcanfores.pagos.controlador;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.alcanfores.comun.error.ErrorApi;
import pe.alcanfores.comun.error.ErrorCampo;

import java.util.List;

/** Errores propios de este servicio que el manejador común trataría como 500. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ManejadorErroresPagos {

    /** {@code ?sort=campoQueNoExiste,desc} */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorApi> ordenInvalido(PropertyReferenceException e, HttpServletRequest req) {
        String mensaje = "No se puede ordenar por «" + e.getPropertyName() + "».";
        return ResponseEntity.badRequest()
                .body(ErrorApi.de(400, "VALIDACION", mensaje, req.getRequestURI(), List.of(new ErrorCampo("sort", mensaje))));
    }
}
