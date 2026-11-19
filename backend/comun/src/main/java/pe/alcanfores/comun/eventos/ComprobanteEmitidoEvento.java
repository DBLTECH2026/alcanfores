package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** comprobante.emitido · tipo = BOLETA, FACTURA o NOTA_CREDITO (simulados, sin validez tributaria). */
public record ComprobanteEmitidoEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idComprobante, String codigoReserva, String tipo, String serieNumero, BigDecimal total, ClienteContacto cliente) implements EventoDominio {
}
