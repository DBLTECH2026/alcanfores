package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Fotografía de la reserva en el momento del evento (los servicios no comparten base de datos). */
public record DatosReserva(Long idReserva, String codigoReserva, Long idCancha, String nombreCancha, String deporte, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, BigDecimal montoTotal, BigDecimal montoAdelanto, BigDecimal montoPagado, ClienteContacto cliente) {
}
