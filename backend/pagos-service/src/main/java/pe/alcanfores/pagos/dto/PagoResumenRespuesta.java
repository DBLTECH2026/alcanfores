package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila del listado de pagos del panel. */
public record PagoResumenRespuesta(Long id, String codigoReserva, Cliente cliente, String metodo, String canal,
                                   String tipoPago, BigDecimal monto, String estado, String codigoOperacion,
                                   boolean tieneArchivo, LocalDateTime fechaEnvio, LocalDateTime fechaValidacion,
                                   String validadoPor, ComprobanteEnlace comprobante) {

    public record Cliente(String dni, String nombres, String apellidos, String telefono, String correo) {
    }
}
