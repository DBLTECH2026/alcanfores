package pe.alcanfores.reservas.cliente;

import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;

import java.time.LocalDate;
import java.util.List;

/**
 * Feriado o día no laborable del calendario de catalogo-service ({@code GET /interno/calendario} o el evento
 * {@code calendario.actualizado}). {@code canchas} nulo o vacío = todas (solo aplica a NO_LABORABLE).
 */
public record DiaEspecial(Long id, LocalDate fecha, String tipo, String nombre, List<Long> canchas) {

    public boolean esFeriado() {
        return CalendarioActualizadoEvento.FERIADO.equalsIgnoreCase(tipo);
    }

    /** ¿Este día no laborable cierra la cancha? (un feriado aplica a todas, cada una según su modo). */
    public boolean aplicaA(Long idCancha) {
        return esFeriado() || canchas == null || canchas.isEmpty() || canchas.contains(idCancha);
    }

    public static DiaEspecial de(CalendarioActualizadoEvento e) {
        return new DiaEspecial(null, e.fecha(), e.tipo(), e.nombre(), e.canchas());
    }
}
