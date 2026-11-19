package pe.alcanfores.pagos.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regla del SPEC §3 para factura: RUC que empiece con 10 o 20, razón social de 3 a 150 caracteres y dirección fiscal
 * de 5 a 200. Marca el error en el campo concreto ({@code comprobante.ruc}, {@code comprobante.razonSocial}…).
 */
@Documented
@Constraint(validatedBy = FacturaCompleta.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FacturaCompleta {

    String MENSAJE = "Para factura necesitas un RUC válido y la razón social.";
    String MENSAJE_DIRECCION = "La dirección fiscal debe tener de 5 a 200 caracteres.";
    String MENSAJE_TIPO = "Elige boleta o factura.";

    String message() default MENSAJE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<FacturaCompleta, DatosComprobanteSolicitud> {

        @Override
        public boolean isValid(DatosComprobanteSolicitud c, ConstraintValidatorContext ctx) {
            if (c == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            if (c.tipo() == null || c.tipo() == TipoComprobante.NOTA_CREDITO) {
                ctx.buildConstraintViolationWithTemplate(MENSAJE_TIPO).addPropertyNode("tipo").addConstraintViolation();
                return false;
            }
            if (!c.esFactura()) {
                return true;
            }
            boolean valido = true;
            String ruc = c.ruc() == null ? "" : c.ruc().trim();
            // Un RUC mal formado ya lo reporta @Ruc; aquí se exige que exista y sea de persona natural (10) o jurídica (20).
            if (ruc.isEmpty() || (Documentos.rucValido(ruc) && !Documentos.rucParaFactura(ruc))) {
                ctx.buildConstraintViolationWithTemplate(MENSAJE).addPropertyNode("ruc").addConstraintViolation();
                valido = false;
            }
            if (!largoEntre(c.razonSocial(), 3, 150)) {
                ctx.buildConstraintViolationWithTemplate(MENSAJE).addPropertyNode("razonSocial").addConstraintViolation();
                valido = false;
            }
            if (!largoEntre(c.direccionFiscal(), 5, 200)) {
                ctx.buildConstraintViolationWithTemplate(MENSAJE_DIRECCION).addPropertyNode("direccionFiscal")
                        .addConstraintViolation();
                valido = false;
            }
            return valido;
        }

        private static boolean largoEntre(String texto, int min, int max) {
            String limpio = Texto.limpiar(texto);
            return limpio != null && limpio.length() >= min && limpio.length() <= max;
        }
    }
}
