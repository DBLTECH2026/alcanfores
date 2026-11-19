package pe.alcanfores.catalogo.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regla entre campos de la cancha: el precio de noche no puede ser menor que el de día. El error se asocia al campo
 * {@code precioNoche} para que el formulario lo marque en su sitio.
 */
@Documented
@Constraint(validatedBy = PreciosCoherentes.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreciosCoherentes {
    String message() default "El precio de noche no puede ser menor que el de día.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<PreciosCoherentes, CanchaSolicitud> {
        @Override
        public boolean isValid(CanchaSolicitud s, ConstraintValidatorContext ctx) {
            if (s == null || s.precioDia() == null || s.precioNoche() == null || s.precioNoche().compareTo(s.precioDia()) >= 0) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("precioNoche").addConstraintViolation();
            return false;
        }
    }
}
