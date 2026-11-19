package pe.alcanfores.reservas.dto;

import pe.alcanfores.reservas.modelo.Franja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Disponibilidad pública de turnos y calendario. */
public final class DisponibilidadDtos {

    private DisponibilidadDtos() {
    }

    public record DisponibilidadRespuesta(LocalDate fecha, List<CanchaDisponibilidad> canchas) {
    }

    public record CanchaDisponibilidad(Long idCancha, String nombre, String deporte, List<TurnoDisponibilidad> turnos) {
    }

    /**
     * estado: DISPONIBLE | OCUPADO | BLOQUEADO | PASADO (empieza en menos de 30 minutos). {@code precio} ya incluye el
     * {@code recargo} de feriado.
     */
    public record TurnoDisponibilidad(Long idTurno, LocalTime horaInicio, LocalTime horaFin, BigDecimal precio, Franja franja,
                                      String estado, boolean feriado, BigDecimal recargo) {
    }

    public record DiaCalendario(LocalDate fecha, long libres, long total) {
    }
}
