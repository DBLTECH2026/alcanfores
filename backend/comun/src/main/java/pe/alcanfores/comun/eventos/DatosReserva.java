package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fotografía de la reserva en el momento del evento (los servicios no comparten base de datos). Una reserva puede tener
 * varios turnos de la misma cancha y fecha: {@code horaInicio} es la del primero, {@code horaFin} la del último,
 * {@code horarioTexto} los describe («7:00–9:00 p. m.» o «9:00–10:00 a. m. y 7:00–8:00 p. m.», ver {@code Horarios.texto})
 * y {@code cantidadTurnos} cuántos son.
 */
public record DatosReserva(Long idReserva, String codigoReserva, Long idCancha, String nombreCancha, String deporte, LocalDate fecha,
                           LocalTime horaInicio, LocalTime horaFin, BigDecimal montoTotal, BigDecimal montoAdelanto,
                           BigDecimal montoPagado, ClienteContacto cliente, String horarioTexto, int cantidadTurnos) {
}
