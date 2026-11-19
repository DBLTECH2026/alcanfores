package pe.alcanfores.pagos.servicio;

import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.pagos.dto.DatosComprobanteSolicitud;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.TipoComprobante;

/** Convierte lo que pidió el cliente (boleta o factura) en los datos del adquirente del comprobante. */
final class FacturacionSolicitada {

    private FacturacionSolicitada() {
    }

    static DatosFacturacion de(DatosComprobanteSolicitud solicitud, String codigoReserva, InstantaneaReserva reserva) {
        DatosFacturacion df = new DatosFacturacion();
        df.setCodigoReserva(codigoReserva);
        if (solicitud != null && solicitud.esFactura()) {
            df.setTipo(TipoComprobante.FACTURA);
            df.setTipoDoc(DatosFacturacion.DOC_RUC);
            df.setNumDoc(solicitud.ruc().trim());
            df.setNombre(Texto.limpiar(solicitud.razonSocial()));
            df.setDireccion(Texto.limpiar(solicitud.direccionFiscal()));
        } else {
            df.setTipo(TipoComprobante.BOLETA);
            df.setTipoDoc(DatosFacturacion.DOC_DNI);
            df.setNumDoc(reserva.getClienteDni());
            df.setNombre(reserva.nombreCompleto());
        }
        return df;
    }
}
