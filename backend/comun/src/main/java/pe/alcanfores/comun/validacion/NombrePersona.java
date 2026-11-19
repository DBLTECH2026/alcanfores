package pe.alcanfores.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.alcanfores.comun.util.Texto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/** Nombres o apellidos: 2 a 60 letras (con tildes y ñ), espacios, apóstrofe o guion. Nulo se considera válido. */
@Documented
@Constraint(validatedBy = NombrePersona.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NombrePersona {
    String message() default "Usa solo letras (2 a 60).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<NombrePersona, String> {
        private static final Pattern PATRON = Pattern.compile("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]{2,60}$");

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            if (valor == null) {
                return true;
            }
            String limpio = Texto.limpiar(valor);
            return PATRON.matcher(limpio).matches() && limpio.chars().anyMatch(Character::isLetter);
        }
    }
}
