package pe.alcanfores.pagos.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.pagos.dto.CobroRespuesta;
import pe.alcanfores.pagos.dto.FormularioComprobante;
import pe.alcanfores.pagos.dto.ListadoPagosRespuesta;
import pe.alcanfores.pagos.dto.MetodosPagoRespuesta;
import pe.alcanfores.pagos.dto.PagoClienteRespuesta;
import pe.alcanfores.pagos.dto.PagoDetalleRespuesta;
import pe.alcanfores.pagos.dto.PagoRegistradoRespuesta;
import pe.alcanfores.pagos.dto.PagoResumenRespuesta;
import pe.alcanfores.pagos.dto.SolicitudCaja;
import pe.alcanfores.pagos.dto.SolicitudCajaInterna;

import java.time.LocalDate;
import java.util.List;

/** Pagos con comprobante Yape/Plin, validación/rechazo y cobros en caja. */
public interface PagoServicio {

    MetodosPagoRespuesta metodos();

    /** El cliente sube la captura de Yape/Plin: pago PENDIENTE por el adelanto + {@code pago.registrado}. */
    PagoRegistradoRespuesta registrarComprobante(FormularioComprobante formulario, byte[] imagen);

    List<PagoClienteRespuesta> pagosDeReserva(String codigoReserva, String dni);

    ListadoPagosRespuesta listar(String estado, String metodo, String canal, LocalDate desde, LocalDate hasta, String q,
                                 Pageable pagina);

    PagoDetalleRespuesta detalle(Long id);

    AlmacenComprobantes.Imagen imagen(Long id);

    /** VALIDADO + comprobante + {@code pago.validado} y {@code comprobante.emitido}. */
    CobroRespuesta validar(Long id);

    /** RECHAZADO con observación + {@code pago.rechazado}. */
    PagoResumenRespuesta rechazar(Long id, String observacion);

    /** Cobro presencial desde el panel. */
    CobroRespuesta cobrarEnCaja(SolicitudCaja solicitud);

    /** Cobro presencial pedido por reservas-service (reserva manual con pago inicial). */
    CobroRespuesta cobrarEnCajaInterno(SolicitudCajaInterna solicitud);
}
