package pe.alcanfores.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Observación al rechazar un comprobante de pago (10 a 255 caracteres): la verá el cliente. */
public record SolicitudRechazo(
        @NotNull(message = Mensajes.MOTIVO) @Size(min = 10, max = 255, message = Mensajes.MOTIVO) String observacion) {
}
