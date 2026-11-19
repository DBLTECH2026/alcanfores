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
import java.nio.charset.StandardCharsets;

/**
 * Política de contraseñas del SPEC §2: 8 a 64 caracteres, al menos una mayúscula, una minúscula y un número.
 * Además no admite más de 72 bytes (límite de BCrypt) ni espacios al inicio o al final. Nulo se considera válido
 * (la obligatoriedad la marca {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = ContrasenaSegura.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {

    String MENSAJE = "La contraseña debe tener de 8 a 64 caracteres, con al menos una mayúscula, una minúscula y un número.";

    String message() default MENSAJE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ContrasenaSegura, String> {

        public static final int MINIMO = 8;
        public static final int MAXIMO = 64;
        private static final int MAXIMO_BYTES_BCRYPT = 72;

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return valor == null || cumple(valor);
        }

        public static boolean cumple(String valor) {
            int largo = valor.codePointCount(0, valor.length());
            return largo >= MINIMO && largo <= MAXIMO
                    && valor.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_BCRYPT
                    && valor.strip().equals(valor)
                    && valor.codePoints().anyMatch(Character::isUpperCase)
                    && valor.codePoints().anyMatch(Character::isLowerCase)
                    && valor.codePoints().anyMatch(Character::isDigit);
        }
    }
}
