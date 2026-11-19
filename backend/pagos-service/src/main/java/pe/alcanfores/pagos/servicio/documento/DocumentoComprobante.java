package pe.alcanfores.pagos.servicio.documento;

import pe.alcanfores.pagos.modelo.Comprobante;

/**
 * Lo necesario para dibujar un comprobante: el comprobante, el que modifica (solo notas de crédito) y la forma de pago
 * legible («Contado · Yape»).
 */
public record DocumentoComprobante(Comprobante comprobante, Comprobante referencia, String formaPago) {
}
