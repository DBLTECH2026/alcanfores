package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago tal como lo ve el cliente en «Mi reserva» ({@code facturacion}: boleta o factura que pidió para ese pago). */
public record PagoClienteRespuesta(Long idPago, String metodo, String canal, String tipoPago, BigDecimal monto,
                                   String estado, String observacion, LocalDateTime fecha,
                                   ComprobanteEnlace comprobante, ComprobanteEnlace notaCredito,
                                   FacturacionPedida facturacion) {
}
