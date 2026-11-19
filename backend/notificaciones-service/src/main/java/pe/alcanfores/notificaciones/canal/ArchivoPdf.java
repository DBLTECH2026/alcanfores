package pe.alcanfores.notificaciones.canal;

/** PDF ya descargado, listo para adjuntar al correo o enviar como documento de WhatsApp. */
public record ArchivoPdf(String nombre, byte[] contenido) {
}
