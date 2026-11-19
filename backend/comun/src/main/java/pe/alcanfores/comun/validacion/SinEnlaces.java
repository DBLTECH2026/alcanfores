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

/** Texto libre sin enlaces ni escrito solo en mayúsculas (reseñas, respuestas). Nulo se considera válido. */
@Documented
@Constraint(validatedBy = SinEnlaces.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SinEnlaces {
    String message() default "Escribe entre 10 y 500 caracteres, sin enlaces.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SinEnlaces, String> {
        private static final Pattern ENLACE = Pattern.compile("(https?://|www\\.|\\b[a-z0-9-]+\\.(com|pe|net|org|io|ly)\\b)");

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            if (valor == null) {
                return true;
            }
            if (ENLACE.matcher(valor.toLowerCase(Locale.ROOT)).find()) {
                return false;
            }
            long letras = valor.chars().filter(Character::isLetter).count();
            long mayusculas = valor.chars().filter(Character::isUpperCase).count();
            return letras < 12 || mayusculas < letras; // «TODO EN MAYÚSCULAS» se rechaza
        }
    }
}
