package pe.alcanfores.catalogo.dto;

import java.time.LocalDateTime;

/** Reseña tal como la ve el portal; {@code verificada} = la dejó un cliente que jugó ({@code origen} CLIENTE). */
public record ResenaPublicaRespuesta(
        Long id,
        String autor,
        Integer puntuacion,
        String comentario,
        LocalDateTime fecha,
        String respuesta,
        LocalDateTime respondidoEn,
        boolean verificada,
        String origen
) {
}
