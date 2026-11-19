package pe.alcanfores.auth.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/**
 * La confirmación debe repetir exactamente la contraseña nueva; el error se marca en el campo {@code confirmacion}.
 * Se aplica a cualquier solicitud que implemente {@link ConConfirmacion} (cambio de contraseña y registro de clientes).
 */
@Documented
@Constraint(validatedBy = ContrasenasCoinciden.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenasCoinciden {

    String message() default "Las contraseñas no coinciden.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Solicitud con una contraseña que se debe repetir. */
    interface ConConfirmacion {
        String contrasenaAConfirmar();

        String confirmacion();
    }

    class Validador implements ConstraintValidator<ContrasenasCoinciden, ConConfirmacion> {

        @Override
        public boolean isValid(ConConfirmacion s, ConstraintValidatorContext ctx) {
            if (s == null || s.contrasenaAConfirmar() == null || s.confirmacion() == null || s.confirmacion().isBlank()) {
                return true;
            }
            if (Objects.equals(s.contrasenaAConfirmar(), s.confirmacion())) {
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
