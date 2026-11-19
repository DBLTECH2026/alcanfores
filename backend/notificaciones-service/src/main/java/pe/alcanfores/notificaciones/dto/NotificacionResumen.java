package pe.alcanfores.notificaciones.dto;

import java.time.LocalDateTime;

/** Fila de la bandeja de notificaciones (sin el cuerpo, que puede pesar). */
public record NotificacionResumen(Long id, String codigoReserva, String evento, String eventoTexto, String canal,
                                  String destino, String asunto, String estado, int intentos, String ultimoError,
                                  int adjuntos, LocalDateTime creadoEn, LocalDateTime enviadoEn) {
}
