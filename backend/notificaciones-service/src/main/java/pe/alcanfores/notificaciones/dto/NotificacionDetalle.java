package pe.alcanfores.notificaciones.dto;

import pe.alcanfores.notificaciones.modelo.Adjunto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle con el cuerpo tal como salió ({@code cuerpo}: HTML del correo con el logo como {@code cid:} o texto del
 * WhatsApp) y {@code vistaPrevia}: el mismo HTML con el logo y la tarjeta incrustados (data URI) para un iframe del
 * panel. {@code imagenTarjeta}: la tarjeta ilustrada como data URI ({@code null} si el aviso no lleva).
 */
public record NotificacionDetalle(Long id, String codigoReserva, String evento, String eventoTexto, String canal,
                                  String destino, String asunto, String estado, int intentos, String ultimoError,
                                  LocalDateTime creadoEn, LocalDateTime enviadoEn, String formato, String cuerpo,
                                  String vistaPrevia, List<Adjunto> adjuntos, boolean reintentable,
                                  String imagenTarjeta) {
}
