package pe.alcanfores.comun.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** reserva.registrada · la publica reservas al crear una reserva PENDIENTE. */
public record ReservaRegistradaEvento(UUID idEvento, LocalDateTime ocurridoEn, DatosReserva reserva, String origen, String metodoPagoPrevisto, LocalDateTime venceEn) implements EventoDominio {
}
