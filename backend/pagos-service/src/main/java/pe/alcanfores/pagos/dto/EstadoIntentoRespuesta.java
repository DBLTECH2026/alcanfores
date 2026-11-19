package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sondeo de la página de resultado ({@code /pago/resultado?ref=…}). */
public record EstadoIntentoRespuesta(String referencia, String proveedor, String estado, String tipoPago,
                                     BigDecimal monto, String codigoReserva, String motivoRechazo,
                                     LocalDateTime expiraEn, Long idPago, ComprobanteEnlace comprobante) {
}
