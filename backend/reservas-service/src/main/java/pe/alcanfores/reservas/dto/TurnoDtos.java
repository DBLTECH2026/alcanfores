package pe.alcanfores.reservas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Franja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Agenda, generación y bloqueo de turnos. */
public final class TurnoDtos {

    private TurnoDtos() {
    }

    public record AgendaRespuesta(LocalDate fecha, List<AgendaCancha> canchas) {
    }

    public record AgendaCancha(Long idCancha, String nombre, String deporte, boolean activa, List<AgendaTurno> turnos) {
    }

    public record AgendaTurno(Long idTurno, LocalTime horaInicio, LocalTime horaFin, BigDecimal precio, Franja franja,
                              EstadoTurno estado, String motivoBloqueo, AgendaReserva reserva) {
    }

    public record AgendaReserva(String codigoReserva, String cliente, String telefono, EstadoReserva estado,
                                EstadoPago estadoPago, Asistencia asistencia) {
    }

    public record GenerarTurnosSolicitud(
            @NotNull(message = Mensajes.FECHA) LocalDate desde,
            @NotNull(message = Mensajes.FECHA) LocalDate hasta,
            List<@NotNull(message = Mensajes.CANCHA) @Positive(message = Mensajes.CANCHA) Long> idsCancha) {
    }

    public record GeneracionRespuesta(int creados, int existentes) {
    }

    public record BloqueoSolicitud(@TextoEntre(min = 3, max = 120, message = Mensajes.MOTIVO_BLOQUEO) String motivo) {
    }

    public record DesbloqueoSolicitud(@Size(max = 120, message = Mensajes.MOTIVO_BLOQUEO) String motivo) {
    }

    public record BloqueoRangoSolicitud(
            @NotNull(message = Mensajes.CANCHA) @Positive(message = Mensajes.CANCHA) Long idCancha,
            @NotNull(message = Mensajes.FECHA) LocalDate fecha,
            @NotNull(message = Mensajes.HORA) LocalTime horaDesde,
            @NotNull(message = Mensajes.HORA) LocalTime horaHasta,
            @TextoEntre(min = 3, max = 120, message = Mensajes.MOTIVO_BLOQUEO) String motivo) {
    }

    /** bloqueados: turnos que pasaron a BLOQUEADO; omitidos: turnos con reserva vigente (no se tocan). */
    public record BloqueoRangoRespuesta(int bloqueados, int omitidos, List<LocalTime> horasOmitidas) {
    }
}
