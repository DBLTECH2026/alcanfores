package pe.alcanfores.pagos.servicio.documento;

import org.springframework.stereotype.Component;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoComprobante;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.servicio.ConfiguracionComercio.Emisor;

import java.time.LocalDateTime;

/**
 * Arma un comprobante a partir del pago (sin tocar la base de datos): montos con IGV incluido, importe en letras,
 * y lo «sella» con el hash SHA-256 de su XML UBL y la cadena del QR.
 */
@Component
public class ArmadorComprobante {

    private final GeneradorXmlUbl xml;

    public ArmadorComprobante(GeneradorXmlUbl xml) {
        this.xml = xml;
    }

    public Comprobante armar(Pago pago, DatosFacturacion facturacion, Emisor emisor, String serie, int correlativo,
                             LocalDateTime fecha) {
        Comprobante c = new Comprobante();
        c.setTipo(facturacion.getTipo());
        c.setCodigoTipo(facturacion.getTipo().codigo());
        c.setSerie(serie);
        c.setCorrelativo(correlativo);
        c.setFechaEmision(fecha);
        c.setCodigoReserva(pago.getCodigoReserva());
        c.setIdPago(pago.getId());
        c.setClienteTipoDoc(facturacion.getTipoDoc());
        c.setClienteNumDoc(facturacion.getNumDoc());
        c.setClienteNombre(facturacion.getNombre());
        c.setClienteDireccion(facturacion.getDireccion());
        c.setDescripcion(ConceptoReserva.lineaComprobante(pago.getReserva(), pago.getTipoPago()));
        montos(c, pago.getMonto());
        emisor(c, emisor.ruc(), emisor.razonSocial(), emisor.direccion());
        c.setEstado(EstadoComprobante.EMITIDO);
        c.setEstadoSunat(Comprobante.ESTADO_SUNAT_SIMULADO);
        sellar(c, null);
        return c;
    }

    /** Nota de crédito por el total del comprobante original (catálogo 09: 01 anulación, 06 devolución total). */
    public Comprobante armarNotaCredito(Comprobante original, String serie, int correlativo, LocalDateTime fecha,
                                        String codigoMotivo, String motivo) {
        Comprobante nc = new Comprobante();
        nc.setTipo(TipoComprobante.NOTA_CREDITO);
        nc.setCodigoTipo(TipoComprobante.NOTA_CREDITO.codigo());
        nc.setSerie(serie);
        nc.setCorrelativo(correlativo);
        nc.setFechaEmision(fecha);
        nc.setCodigoReserva(original.getCodigoReserva());
        nc.setIdPago(original.getIdPago());
        nc.setClienteTipoDoc(original.getClienteTipoDoc());
        nc.setClienteNumDoc(original.getClienteNumDoc());
        nc.setClienteNombre(original.getClienteNombre());
        nc.setClienteDireccion(original.getClienteDireccion());
        nc.setDescripcion(original.getDescripcion());
        montos(nc, original.getTotal());
        emisor(nc, original.getEmisorRuc(), original.getEmisorRazonSocial(), original.getEmisorDireccion());
        nc.setEstado(EstadoComprobante.EMITIDO);
        nc.setEstadoSunat(Comprobante.ESTADO_SUNAT_SIMULADO);
        nc.setIdReferencia(original.getId());
        nc.setCodigoMotivo(codigoMotivo);
        nc.setMotivo(motivo);
        sellar(nc, original);
        return nc;
    }

    /** Serie de notas de crédito según el comprobante que modifican. */
    public static String serieNotaPara(Comprobante original) {
        return original.getTipo() == TipoComprobante.FACTURA ? "FC01" : "BC01";
    }

    public static String seriePara(TipoComprobante tipo) {
        return tipo == TipoComprobante.FACTURA ? "F001" : "B001";
    }

    public String xml(Comprobante c, Comprobante referencia) {
        return xml.generar(c, referencia);
    }

    private void sellar(Comprobante c, Comprobante referencia) {
        c.setHash(HuellaComprobante.sha256Base64(xml.generar(c, referencia)));
        c.setCadenaQr(HuellaComprobante.cadenaQr(c));
    }

    private static void montos(Comprobante c, java.math.BigDecimal total) {
        c.setTotal(Dinero.redondear(total));
        c.setOpGravada(Dinero.baseImponible(total));
        c.setIgv(Dinero.igvIncluido(total));
        c.setMontoLetras(Dinero.enLetras(total));
    }

    private static void emisor(Comprobante c, String ruc, String razonSocial, String direccion) {
        c.setEmisorRuc(ruc);
        c.setEmisorRazonSocial(razonSocial);
        c.setEmisorDireccion(direccion);
    }
}
