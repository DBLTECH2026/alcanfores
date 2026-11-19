package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Comprobante simulado para el panel. */
public record ComprobanteRespuesta(Long id, String tipo, String codigoTipo, String serieNumero, LocalDateTime fechaEmision,
                                   String codigoReserva, Long idPago, String clienteTipoDoc, String clienteNumDoc,
                                   String clienteNombre, String clienteDireccion, String descripcion,
                                   BigDecimal opGravada, BigDecimal igv, BigDecimal total, String montoLetras,
                                   String valorResumen, String cadenaQr, String estado, String estadoSunat,
                                   Long idReferencia, String serieNumeroReferencia, String motivo, String urlPdf,
                                   String urlXml) {
}
