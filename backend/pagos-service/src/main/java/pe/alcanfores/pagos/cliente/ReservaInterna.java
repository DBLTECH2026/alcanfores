package pe.alcanfores.pagos.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.Horarios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Respuesta de reservas-service {@code GET /interno/reservas/{codigo}}: DatosReserva + estado, estadoPago,
 * montoPagado, saldo e inicio (fecha-hora del primer turno). Desde la v2 una reserva puede tener varios turnos:
 * {@code horarioTexto} y {@code cantidadTurnos} pueden faltar si reservas-service aún es v1 (ver {@link #horario()}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaInterna(Long idReserva, String codigoReserva, Long idCancha, String nombreCancha, String deporte,
                             LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, BigDecimal montoTotal,
                             BigDecimal montoAdelanto, BigDecimal montoPagado, ClienteContacto cliente, String estado,
                             String estadoPago, BigDecimal saldo, LocalDateTime inicio, String horarioTexto,
                             Integer cantidadTurnos) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String CANCELADA = "CANCELADA";

    /** Desde la fotografía que manda reservas-service en la reserva manual (aún sin pagos). */
    public static ReservaInterna de(DatosReserva d) {
        BigDecimal pagado = d.montoPagado() == null ? BigDecimal.ZERO : d.montoPagado();
        return new ReservaInterna(d.idReserva(), d.codigoReserva(), d.idCancha(), d.nombreCancha(), d.deporte(), d.fecha(),
                d.horaInicio(), d.horaFin(), d.montoTotal(), d.montoAdelanto(), pagado, d.cliente(),
                pagado.signum() > 0 ? CONFIRMADA : PENDIENTE, pagado.signum() > 0 ? "ADELANTO_PAGADO" : "SIN_PAGO",
                d.montoTotal().subtract(pagado),
                d.fecha() != null && d.horaInicio() != null ? LocalDateTime.of(d.fecha(), d.horaInicio()) : null,
                d.horarioTexto(), d.cantidadTurnos());
    }

    /** «9:00–10:00 a. m. y 7:00–8:00 p. m.»; si reservas no lo manda, el rango del primer al último turno. */
    public String horario() {
        if (horarioTexto != null && !horarioTexto.isBlank()) {
            return horarioTexto;
        }
        return horaInicio == null || horaFin == null ? null : Horarios.rango(horaInicio, horaFin);
    }

    /** Cantidad de turnos (1 si reservas no la manda). */
    public int turnos() {
        return cantidadTurnos == null || cantidadTurnos < 1 ? 1 : cantidadTurnos;
    }

    public BigDecimal pagado() {
        return montoPagado == null ? BigDecimal.ZERO : montoPagado;
    }

    /** Saldo pendiente: el que informa reservas o, si no viene, total − pagado. */
    public BigDecimal saldoPendiente() {
        if (saldo != null) {
            return saldo;
        }
        return montoTotal == null ? BigDecimal.ZERO : montoTotal.subtract(pagado());
    }

    public String dniCliente() {
        return cliente == null ? null : cliente.dni();
    }
}
