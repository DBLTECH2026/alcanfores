package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** pago.validado · tipoPago = ADELANTO, TOTAL o SALDO; validadoPor = usuario o «Pasarela». */
public record PagoValidadoEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idPago, String codigoReserva, String metodo, String canal, BigDecimal monto, String tipoPago, String validadoPor) implements EventoDominio {
}
