package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** pago.registrado · el cliente subió su comprobante o inició un pago. */
public record PagoRegistradoEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idPago, String codigoReserva, String metodo, String canal, BigDecimal monto, String tipoPago) implements EventoDominio {
}
