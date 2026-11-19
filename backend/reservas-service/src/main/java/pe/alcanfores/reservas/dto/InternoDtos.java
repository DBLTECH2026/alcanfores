package pe.alcanfores.reservas.dto;

import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Respuestas de las rutas {@code /interno/**} (solo para otros microservicios). */
public final class InternoDtos {

    private InternoDtos() {
    }

    /** Campos de {@code DatosReserva} + estado, estadoPago, montoPagado, saldo e inicio. */
    public record ReservaInterna(Long idReserva, String codigoReserva, Long idCancha, String nombreCancha, String deporte,
                                 LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, BigDecimal montoTotal,
                                 BigDecimal montoAdelanto, BigDecimal montoPagado, ClienteContacto cliente,
                                 EstadoReserva estado, EstadoPago estadoPago, Asistencia asistencia, Origen origen,
                                 String metodoPagoPrevisto, BigDecimal saldo, LocalDateTime inicio, LocalDateTime venceEn) {
    }

    public record ElegibilidadResena(boolean elegible, String motivo, String autor) {
    }

    /** Reserva sembrada (la usa pagos-service para crear pagos y comprobantes coherentes con la semilla). */
    public record ReservaSemilla(String codigoReserva, EstadoReserva estado, EstadoPago estadoPago, String metodoPagoPrevisto,
                                 BigDecimal montoTotal, BigDecimal montoAdelanto, BigDecimal montoPagado,
                                 LocalDateTime fechaRegistro, LocalDateTime confirmadaEn, Origen origen,
                                 String nombreCancha, LocalDate fecha, LocalTime horaInicio, ClienteContacto cliente) {
    }
}
