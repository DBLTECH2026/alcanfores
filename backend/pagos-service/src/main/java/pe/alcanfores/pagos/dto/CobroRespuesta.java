package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;

/** Resultado de un cobro validado (caja o validación de comprobante): el pago y su comprobante emitido. */
public record CobroRespuesta(Long idPago, String estado, BigDecimal monto, ComprobanteEnlace comprobante) {
}
