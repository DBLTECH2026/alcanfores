package pe.alcanfores.catalogo.dto;

import java.time.LocalDateTime;

/** Reseña en el panel ({@code fecha} = {@code creadoEn}; {@code origen} CLIENTE o COMPLEJO). */
public record ResenaAdminRespuesta(
        Long id,
        Long idCancha,
        String cancha,
        String codigoReserva,
        String autor,
        Integer puntuacion,
        String comentario,
        String estado,
        String respuesta,
        LocalDateTime respondidoEn,
        LocalDateTime creadoEn,
        LocalDateTime fecha,
        String origen
) {
}
