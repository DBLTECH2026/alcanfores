package pe.alcanfores.pagos.mapeador;

import pe.alcanfores.pagos.dto.FacturacionPedida;
import pe.alcanfores.pagos.dto.PagoClienteRespuesta;
import pe.alcanfores.pagos.dto.PagoDetalleRespuesta;
import pe.alcanfores.pagos.dto.PagoResumenRespuesta;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.util.List;

/** Pago → respuestas del panel y del cliente. */
public final class PagoMapeador {

    private PagoMapeador() {
    }

    public static PagoResumenRespuesta.Cliente cliente(InstantaneaReserva r) {
        return new PagoResumenRespuesta.Cliente(r.getClienteDni(), r.getClienteNombres(), r.getClienteApellidos(),
                r.getClienteTelefono(), r.getClienteCorreo());
    }

    public static PagoResumenRespuesta resumen(Pago p, List<Comprobante> delPago) {
        return new PagoResumenRespuesta(p.getId(), p.getCodigoReserva(), cliente(p.getReserva()), p.getMetodo().name(),
                p.getCanal().name(), p.getTipoPago().name(), p.getMonto(), p.getEstado().name(), p.getCodigoOperacion(),
                p.getArchivoComprobante() != null, p.getFechaEnvio(), p.getFechaValidacion(), p.getValidadoPor(),
                ComprobanteMapeador.principal(delPago).map(ComprobanteMapeador::enlaceAdmin).orElse(null));
    }

    public static PagoDetalleRespuesta detalle(Pago p, DatosFacturacion df, List<Comprobante> delPago) {
        PagoDetalleRespuesta.Facturacion facturacion = df == null ? null : new PagoDetalleRespuesta.Facturacion(
                df.getTipo().name(), df.getTipoDoc(), df.getNumDoc(), df.getNombre(), df.getDireccion());
        return new PagoDetalleRespuesta(p.getId(), p.getCodigoReserva(), cliente(p.getReserva()),
                p.getReserva().getConcepto(), p.getMetodo().name(), p.getCanal().name(), p.getTipoPago().name(),
                p.getMonto(), p.getEstado().name(), p.getCodigoOperacion(),
                p.getArchivoComprobante() == null ? null : "/api/admin/pagos/" + p.getId() + "/archivo",
                p.getObservacion(), p.getReferenciaPasarela(), p.getMarcaTarjeta(), p.getUltimos4(),
                p.getCodigoAutorizacion(), p.getFechaEnvio(), p.getFechaValidacion(), p.getValidadoPor(), facturacion,
                delPago.stream().map(ComprobanteMapeador::enlaceAdmin).toList(),
                p.getEstado() == EstadoValidacion.PENDIENTE);
    }

    public static PagoClienteRespuesta paraCliente(Pago p, List<Comprobante> delPago, String dni, DatosFacturacion df) {
        return new PagoClienteRespuesta(p.getId(), p.getMetodo().name(), p.getCanal().name(), p.getTipoPago().name(),
                p.getMonto(), p.getEstado().name(), p.getObservacion(), p.getFechaEnvio(),
                ComprobanteMapeador.principal(delPago).map(c -> ComprobanteMapeador.enlaceCliente(c, dni)).orElse(null),
                ComprobanteMapeador.notaCredito(delPago).map(c -> ComprobanteMapeador.enlaceCliente(c, dni)).orElse(null),
                facturacionPedida(df));
    }

    /** Factura: RUC, razón social y dirección; boleta: solo el tipo (el DNI y el nombre son los del cliente). */
    public static FacturacionPedida facturacionPedida(DatosFacturacion df) {
        if (df == null) {
            return null;
        }
        if (df.getTipo() == TipoComprobante.FACTURA) {
            return new FacturacionPedida(df.getTipo().name(), df.getNumDoc(), df.getNombre(), df.getDireccion());
        }
        return new FacturacionPedida(df.getTipo().name(), null, null, null);
    }
}
