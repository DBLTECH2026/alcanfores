package pe.alcanfores.pagos.mapeador;

import org.springframework.web.util.UriComponentsBuilder;
import pe.alcanfores.pagos.dto.ComprobanteEnlace;
import pe.alcanfores.pagos.dto.ComprobanteRespuesta;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.util.List;
import java.util.Optional;

/** Comprobante → respuestas del panel y del cliente. */
public final class ComprobanteMapeador {

    private ComprobanteMapeador() {
    }

    public static ComprobanteEnlace enlaceAdmin(Comprobante c) {
        String base = "/api/admin/comprobantes/" + c.getId();
        return new ComprobanteEnlace(c.getId(), c.getTipo().name(), c.serieNumero(), c.getEstado().name(),
                base + "/pdf", base + "/xml");
    }

    /** Enlace público: se descarga con el código de reserva y el DNI del cliente ({@code dni} puede faltar). */
    public static ComprobanteEnlace enlaceCliente(Comprobante c, String dni) {
        return new ComprobanteEnlace(c.getId(), c.getTipo().name(), c.serieNumero(), c.getEstado().name(),
                urlCliente(c, "pdf", dni), urlCliente(c, "xml", dni));
    }

    private static String urlCliente(Comprobante c, String extension, String dni) {
        UriComponentsBuilder u = UriComponentsBuilder.fromPath("/api/comprobantes/" + c.serieNumero() + "." + extension)
                .queryParam("codigo", c.getCodigoReserva());
        if (dni != null) {
            u.queryParam("dni", dni);
        }
        return u.build().toUriString();
    }

    public static ComprobanteRespuesta respuesta(Comprobante c, Comprobante referencia) {
        String base = "/api/admin/comprobantes/" + c.getId();
        return new ComprobanteRespuesta(c.getId(), c.getTipo().name(), c.getCodigoTipo(), c.serieNumero(),
                c.getFechaEmision(), c.getCodigoReserva(), c.getIdPago(), c.getClienteTipoDoc(), c.getClienteNumDoc(),
                c.getClienteNombre(), c.getClienteDireccion(), c.getDescripcion(), c.getOpGravada(), c.getIgv(),
                c.getTotal(), c.getMontoLetras(), c.valorResumen(), c.getCadenaQr(), c.getEstado().name(),
                c.getEstadoSunat(), c.getIdReferencia(), referencia == null ? null : referencia.serieNumero(),
                c.getMotivo(), base + "/pdf", base + "/xml");
    }

    /** Boleta o factura del pago (no la nota de crédito). */
    public static Optional<Comprobante> principal(List<Comprobante> delPago) {
        return delPago.stream().filter(c -> c.getTipo() != TipoComprobante.NOTA_CREDITO).findFirst();
    }

    public static Optional<Comprobante> notaCredito(List<Comprobante> delPago) {
        return delPago.stream().filter(c -> c.getTipo() == TipoComprobante.NOTA_CREDITO).reduce((a, b) -> b);
    }
}
