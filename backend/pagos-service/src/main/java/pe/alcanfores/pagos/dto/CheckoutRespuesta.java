package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Datos que muestra la página de checkout de la pasarela beta. */
public record CheckoutRespuesta(String referencia, String comercio, String concepto, BigDecimal monto, String moneda,
                                String estado, LocalDateTime expiraEn, String codigoReserva, String motivoRechazo) {
}
