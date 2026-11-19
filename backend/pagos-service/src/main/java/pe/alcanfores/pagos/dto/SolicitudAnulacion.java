package pe.alcanfores.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Motivo para anular un comprobante con nota de crédito (10 a 255 caracteres). */
public record SolicitudAnulacion(
        @NotNull(message = Mensajes.MOTIVO) @Size(min = 10, max = 255, message = Mensajes.MOTIVO) String motivo) {
}
