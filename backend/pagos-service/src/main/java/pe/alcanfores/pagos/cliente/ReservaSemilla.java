package pe.alcanfores.pagos.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pe.alcanfores.comun.eventos.ClienteContacto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Elemento de {@code GET /interno/reservas/para-semilla}. Los datos de la cancha y del turno son opcionales: si
 * reservas los envía, el comprobante de ejemplo describe el alquiler completo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaSemilla(String codigoReserva, String estado, String estadoPago, String metodoPagoPrevisto,
                             BigDecimal montoTotal, BigDecimal montoAdelanto, BigDecimal montoPagado,
                             LocalDateTime fechaRegistro, LocalDateTime confirmadaEn, ClienteContacto cliente,
                             String nombreCancha, String deporte, LocalDate fecha, LocalTime horaInicio,
                             LocalTime horaFin, BigDecimal montoDevolucion) {
}
