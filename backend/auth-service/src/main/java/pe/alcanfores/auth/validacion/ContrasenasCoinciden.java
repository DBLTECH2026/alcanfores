package pe.alcanfores.auth.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/** La confirmación debe repetir exactamente la contraseña nueva; el error se marca en el campo {@code confirmacion}. */
@Documented
@Constraint(validatedBy = ContrasenasCoinciden.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenasCoinciden {

    String message() default "Las contraseñas no coinciden.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ContrasenasCoinciden, CambiarContrasenaSolicitud> {

        @Override
        public boolean isValid(CambiarContrasenaSolicitud s, ConstraintValidatorContext ctx) {
            if (s == null || s.nueva() == null || s.confirmacion() == null || s.confirmacion().isBlank()) {
                return true;
            }
            if (Objects.equals(s.nueva(), s.confirmacion())) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmacion")
                    .addConstraintViolation();
            return false;
        }
    }
}
