package pe.alcanfores.notificaciones.dto;

/**
 * {@code qrBase64}: imagen del QR como data URL ({@code data:image/png;base64,...}) para un {@code <img>};
 * {@code codigoEmparejamiento}: código de 8 caracteres para vincular sin cámara (solo si se envió el teléfono).
 */
public record ConexionWhatsappRespuesta(String qrBase64, String codigoEmparejamiento, String estado, String mensaje) {
}
