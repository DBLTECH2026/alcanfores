package pe.alcanfores.reservas.dto;

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

/**
 * Texto obligatorio cuya longitud (sin espacios sobrantes) está entre {@code min} y {@code max}. Reemplaza a la pareja
 * {@code @NotBlank + @Size} para que cada campo muestre UN solo mensaje.
 */
@Documented
@Constraint(validatedBy = TextoEntre.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TextoEntre {

    int min();

    int max();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TextoEntre, String> {
        private int min;
        private int max;

        @Override
        public void initialize(TextoEntre a) {
            this.min = a.min();
            this.max = a.max();
        }

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            if (valor == null) {
                return false;
            }
            int largo = Texto.limpiar(valor).length();
            return largo >= min && largo <= max;
        }
    }
}
