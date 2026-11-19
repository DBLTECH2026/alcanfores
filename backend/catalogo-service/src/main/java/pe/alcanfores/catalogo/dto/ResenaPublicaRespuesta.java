package pe.alcanfores.catalogo.dto;

import java.time.LocalDateTime;

/** Reseña tal como la ve el portal: siempre verificada (solo la deja quien tuvo una reserva válida). */
public record ResenaPublicaRespuesta(
        Long id,
        String autor,
        Integer puntuacion,
        String comentario,
        LocalDateTime fecha,
        String respuesta,
        LocalDateTime respondidoEn,
        boolean verificada
) {
}
