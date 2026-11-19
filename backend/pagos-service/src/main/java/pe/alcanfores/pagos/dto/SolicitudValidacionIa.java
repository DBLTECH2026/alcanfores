package pe.alcanfores.pagos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validación automática de un comprobante Yape/Plin por Canchita (asistente con IA). {@code lectura} es lo que la IA
 * leyó de la foto (JSON u texto); se guarda tal cual en el pago. {@code validadoPor} por defecto «Canchita (IA)».
 */
public record SolicitudValidacionIa(
        @NotNull(message = "Envía la lectura del comprobante.") JsonNode lectura,
        @Size(max = 60, message = "El nombre de quien valida tiene hasta 60 caracteres.") String validadoPor) {
}
