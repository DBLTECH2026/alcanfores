package pe.alcanfores.notificaciones.plantilla;

/** Un aviso ya redactado para los dos canales: asunto, HTML del correo y texto del WhatsApp. */
public record MensajeRedactado(String asunto, String html, String texto) {
}
