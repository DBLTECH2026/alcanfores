package pe.alcanfores.pagos.dto;

/**
 * Comprobante que pidió el cliente para un pago, con la misma forma que {@code comprobante} de las solicitudes
 * ({@link DatosComprobanteSolicitud}): el front puede reenviarlo tal cual. En una boleta solo viene {@code tipo}.
 */
public record FacturacionPedida(String tipo, String ruc, String razonSocial, String direccionFiscal) {
}
