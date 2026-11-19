package pe.alcanfores.pagos.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.pagos.dto.ComprobanteRespuesta;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.Pago;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Emisión, anulación (nota de crédito), consulta y descarga de comprobantes simulados. */
public interface ComprobanteServicio {

    String MOTIVO_ANULACION = "01";
    String MOTIVO_DEVOLUCION = "06";

    /** Boleta o factura por un cobro validado (requiere transacción activa). */
    Comprobante emitirParaPago(Pago pago, LocalDateTime fecha, boolean publicarEvento);

    /** Nota de crédito por el total del comprobante (requiere transacción activa); el original queda ANULADO. */
    Comprobante emitirNotaCredito(Comprobante original, String codigoMotivo, String motivo, LocalDateTime fecha,
                                  boolean publicarEvento);

    /** Acción ADMIN: anula con nota de crédito (catálogo 09, código 01). */
    ComprobanteRespuesta anular(Long id, String motivo);

    PaginaRespuesta<ComprobanteRespuesta> listar(String tipo, String estado, LocalDate desde, LocalDate hasta, String q,
                                                 Pageable pagina);

    ComprobanteRespuesta obtener(Long id);

    Archivo pdf(Long id);

    Archivo xml(Long id);

    /** Descarga pública: la serie-número debe ser de esa reserva y el DNI el del cliente que pagó. */
    Archivo pdfParaCliente(String serieNumero, String codigoReserva, String dni);

    Archivo xmlParaCliente(String serieNumero, String codigoReserva, String dni);

    record Archivo(String nombre, String tipoContenido, byte[] contenido) {
    }
}
