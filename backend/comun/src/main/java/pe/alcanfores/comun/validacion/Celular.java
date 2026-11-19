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

/**
 * Celular peruano: 9 dígitos que empiezan con 9. Acepta espacios, guiones o el prefijo +51; el servicio debe guardar
 * {@code Texto.normalizarCelular(valor)}. Nulo se considera válido.
 */
@Documented
@Constraint(validatedBy = Celular.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Celular {
    String message() default "Ingresa un celular peruano de 9 dígitos que empiece con 9.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Celular, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return valor == null || Documentos.celularValido(Texto.normalizarCelular(valor));
        }
    }
}
