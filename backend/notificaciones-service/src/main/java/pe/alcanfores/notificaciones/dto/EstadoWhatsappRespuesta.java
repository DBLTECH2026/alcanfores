package pe.alcanfores.notificaciones.dto;

/** {@code estado}: {@code open}, {@code connecting}, {@code close} o {@code NO_CONFIGURADO}; {@code numero} si está conectado. */
public record EstadoWhatsappRespuesta(boolean configurado, String instancia, String estado, String numero) {
}
