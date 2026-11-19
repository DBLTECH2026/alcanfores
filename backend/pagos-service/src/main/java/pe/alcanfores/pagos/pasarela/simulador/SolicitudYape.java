package pe.alcanfores.pagos.pasarela.simulador;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.pagos.dto.Mensajes;

/** Pago con Yape en la pasarela beta: celular + código de aprobación (123456 aprueba, 000000 rechaza). */
public record SolicitudYape(
        @NotNull(message = Mensajes.CELULAR) @Celular String celular,
        @NotNull(message = Mensajes.CODIGO_APROBACION) @Pattern(regexp = "\\d{6}", message = Mensajes.CODIGO_APROBACION)
        String codigoAprobacion) {
}
