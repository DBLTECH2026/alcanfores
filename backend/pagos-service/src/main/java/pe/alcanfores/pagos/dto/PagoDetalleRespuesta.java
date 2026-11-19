package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Detalle de un pago para el panel (con la imagen del comprobante y los comprobantes emitidos). */
public record PagoDetalleRespuesta(Long id, String codigoReserva, PagoResumenRespuesta.Cliente cliente, String concepto,
                                   String metodo, String canal, String tipoPago, BigDecimal monto, String estado,
                                   String codigoOperacion, String urlArchivo, String observacion,
                                   String referenciaPasarela, String marcaTarjeta, String ultimos4,
                                   String codigoAutorizacion, LocalDateTime fechaEnvio, LocalDateTime fechaValidacion,
                                   String validadoPor, Facturacion facturacion, List<ComprobanteEnlace> comprobantes,
                                   boolean puedeValidar) {

    public record Facturacion(String tipo, String tipoDoc, String numDoc, String nombre, String direccion) {
    }
}
