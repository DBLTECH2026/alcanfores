package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** reserva.confirmada · la publica reservas cuando se valida el adelanto o el total. */
public record ReservaConfirmadaEvento(UUID idEvento, LocalDateTime ocurridoEn, DatosReserva reserva, BigDecimal saldoPendiente) implements EventoDominio {
}
