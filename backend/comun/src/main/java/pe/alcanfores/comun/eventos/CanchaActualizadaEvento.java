package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** cancha.actualizada · la publica catálogo al crear o editar una cancha. */
public record CanchaActualizadaEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idCancha, String nombre, String deporte, BigDecimal precioDia, BigDecimal precioNoche, String estado) implements EventoDominio {
}
