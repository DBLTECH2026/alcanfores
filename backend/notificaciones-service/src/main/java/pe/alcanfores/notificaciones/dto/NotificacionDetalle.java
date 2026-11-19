package pe.alcanfores.notificaciones.dto;

import pe.alcanfores.notificaciones.modelo.Adjunto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle con el cuerpo tal como salió ({@code cuerpo}: HTML del correo con el logo como {@code cid:} o texto del
 * WhatsApp) y {@code vistaPrevia}: el mismo HTML con el logo incrustado (data URI) para mostrarlo en un iframe del panel.
 */
public record NotificacionDetalle(Long id, String codigoReserva, String evento, String eventoTexto, String canal,
                                  String destino, String asunto, String estado, int intentos, String ultimoError,
                                  LocalDateTime creadoEn, LocalDateTime enviadoEn, String formato, String cuerpo,
                                  String vistaPrevia, List<Adjunto> adjuntos, boolean reintentable) {
}
