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

/** DNI peruano: exactamente 8 dígitos. Nulo se considera válido (combinar con @NotBlank si es obligatorio). */
@Documented
@Constraint(validatedBy = Dni.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Dni {
    String message() default "El DNI debe tener 8 dígitos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Dni, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return valor == null || Documentos.dniValido(valor.trim());
        }
    }
}
