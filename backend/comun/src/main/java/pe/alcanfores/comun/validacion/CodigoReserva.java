package pe.alcanfores.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.regex.Pattern;

/** Código de reserva «ALC-XXXXXX» (sin I, O, 0 ni 1 para que no se confundan). Nulo se considera válido. */
@Documented
@Constraint(validatedBy = CodigoReserva.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoReserva {
    String message() default "El código tiene el formato ALC-XXXXXX.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    Pattern PATRON = Pattern.compile("^ALC-[A-HJ-NP-Z2-9]{6}$");

    class Validador implements ConstraintValidator<CodigoReserva, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return valor == null || PATRON.matcher(valor.trim().toUpperCase(Locale.ROOT)).matches();
        }
    }
}
