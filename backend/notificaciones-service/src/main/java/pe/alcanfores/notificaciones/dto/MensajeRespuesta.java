package pe.alcanfores.notificaciones.dto;

/** Respuesta simple de una acción: estado resultante y mensaje para el toast del panel. */
public record MensajeRespuesta(String estado, String mensaje) {
}
