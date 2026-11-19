package pe.alcanfores.pagos.pasarela.simulador;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.pagos.dto.Mensajes;

/** Código de verificación 3-D Secure (en la beta, 123456 aprueba). */
public record Solicitud3ds(
        @NotNull(message = Mensajes.CODIGO_3DS) @Pattern(regexp = "\\d{6}", message = Mensajes.CODIGO_3DS) String codigo) {
}
