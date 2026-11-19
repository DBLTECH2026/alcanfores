package pe.alcanfores.pagos.dto;

import pe.alcanfores.comun.validacion.Ruc;
import pe.alcanfores.pagos.modelo.TipoComprobante;

/**
 * Qué comprobante pide el cliente: boleta (a su DNI) o factura (RUC 10/20 + razón social + dirección fiscal).
 * Si no se envía, se emite boleta.
 */
@FacturaCompleta
public record DatosComprobanteSolicitud(TipoComprobante tipo, @Ruc String ruc, String razonSocial, String direccionFiscal) {

    public static DatosComprobanteSolicitud boleta() {
        return new DatosComprobanteSolicitud(TipoComprobante.BOLETA, null, null, null);
    }

    public boolean esFactura() {
        return tipo == TipoComprobante.FACTURA;
    }
}
