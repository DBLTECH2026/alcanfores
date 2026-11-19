package pe.alcanfores.pagos.servicio;

import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.pagos.dto.DatosComprobanteSolicitud;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.util.Optional;

/**
 * Convierte lo que pidió el cliente (boleta o factura) en los datos del adquirente del comprobante. Si el envío NO trae
 * {@code comprobante} (reenvío tras un rechazo, pago en línea desde «Mi reserva», cobro del saldo en caja) se repite la
 * última facturación pedida para la misma reserva: así la factura pedida al reservar no se pierde. Un
 * {@code {"tipo":"BOLETA"}} explícito sí cambia a boleta.
 */
final class FacturacionSolicitada {

    private FacturacionSolicitada() {
    }

    static DatosFacturacion de(DatosComprobanteSolicitud solicitud, String codigoReserva, InstantaneaReserva reserva,
                               Optional<DatosFacturacion> anterior) {
        if (solicitud == null && anterior.isPresent()) {
            DatosFacturacion previa = anterior.get();
            DatosFacturacion df = new DatosFacturacion();
            df.setCodigoReserva(codigoReserva);
            df.setTipo(previa.getTipo());
            df.setTipoDoc(previa.getTipoDoc());
            df.setNumDoc(previa.getNumDoc());
            df.setNombre(previa.getNombre());
            df.setDireccion(previa.getDireccion());
            return df;
        }
        return de(solicitud, codigoReserva, reserva);
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
