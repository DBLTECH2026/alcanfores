package pe.alcanfores.pagos.dto;

import java.time.LocalDateTime;

/** Preferencia de Mercado Pago: el front redirige a {@code initPoint} (sandbox si el token es de prueba). */
public record PreferenciaRespuesta(String referencia, String initPoint, boolean sandbox, LocalDateTime expiraEn) {
}
