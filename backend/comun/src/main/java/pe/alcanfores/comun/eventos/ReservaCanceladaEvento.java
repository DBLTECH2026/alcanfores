package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** reserva.cancelada · canceladoPor = CLIENTE, ADMINISTRADOR o SISTEMA. */
public record ReservaCanceladaEvento(UUID idEvento, LocalDateTime ocurridoEn, DatosReserva reserva, String canceladoPor, String motivo, boolean devolucion, BigDecimal montoDevolucion) implements EventoDominio {
}
