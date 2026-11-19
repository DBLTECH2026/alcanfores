package pe.alcanfores.pagos.servicio;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.Comparator;
import java.util.List;

/**
 * Valida con Bean Validation objetos que no llegan como {@code @RequestBody} (formularios multipart) y junta todos los
 * errores por campo en un único 400 {@code VALIDACION}, igual que el manejador de {@code comun}.
 */
@Component
public class ValidadorSolicitudes {

    private final Validator validador;

    public ValidadorSolicitudes(Validator validador) {
        this.validador = validador;
    }

    public List<ErrorCampo> errores(Object objeto) {
        return validador.validate(objeto).stream()
                .sorted(Comparator.comparing((ConstraintViolation<Object> v) -> v.getPropertyPath().toString()))
                .map(v -> new ErrorCampo(v.getPropertyPath().toString(), v.getMessage()))
                .distinct()
                .toList();
    }

    public static void lanzarSiHay(List<ErrorCampo> errores) {
        if (!errores.isEmpty()) {
            String mensaje = errores.size() == 1 ? errores.get(0).mensaje() : "Revisa los datos marcados.";
            throw new NegocioExcepcion(400, "VALIDACION", mensaje, errores);
        }
    }
}
