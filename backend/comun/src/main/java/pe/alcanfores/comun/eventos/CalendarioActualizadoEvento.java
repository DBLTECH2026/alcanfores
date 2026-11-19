package pe.alcanfores.comun.eventos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * calendario.actualizado · la publica catálogo al crear, editar o borrar un día especial. {@code tipo} FERIADO | NO_LABORABLE;
 * {@code canchas} = ids afectados (nulo = todas; solo NO_LABORABLE). {@code eliminado} = el día dejó de ser especial (si se
 * editó cambiando la fecha, llega uno con la fecha vieja y {@code eliminado = true} y otro con la nueva). reservas-service
 * reevalúa los turnos de ESA fecha.
 */
public record CalendarioActualizadoEvento(UUID idEvento, LocalDateTime ocurridoEn, LocalDate fecha, String tipo, String nombre,
                                          List<Long> canchas, boolean eliminado) implements EventoDominio {

    public static final String FERIADO = "FERIADO";
    public static final String NO_LABORABLE = "NO_LABORABLE";
}
