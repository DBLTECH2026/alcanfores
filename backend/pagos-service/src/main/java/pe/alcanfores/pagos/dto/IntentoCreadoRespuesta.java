package pe.alcanfores.pagos.dto;

import java.time.LocalDateTime;

/** Intento en la pasarela beta: el front abre {@code urlCheckout} ({@code /pasarela/ALP-…}). */
public record IntentoCreadoRespuesta(String referencia, String urlCheckout, LocalDateTime expiraEn) {
}
