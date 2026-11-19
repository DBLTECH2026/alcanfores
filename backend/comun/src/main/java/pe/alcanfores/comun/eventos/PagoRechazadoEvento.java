package pe.alcanfores.comun.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** pago.rechazado · con la observación que verá el cliente. */
public record PagoRechazadoEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idPago, String codigoReserva, String observacion, String rechazadoPor) implements EventoDominio {
}
