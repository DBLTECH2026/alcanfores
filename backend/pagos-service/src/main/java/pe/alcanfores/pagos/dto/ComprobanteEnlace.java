package pe.alcanfores.pagos.dto;

/** Referencia corta a un comprobante con sus enlaces de descarga. */
public record ComprobanteEnlace(Long id, String tipo, String serieNumero, String estado, String urlPdf, String urlXml) {
}
