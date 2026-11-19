package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;

/** Tarjeta de cancha del portal ({@code GET /api/catalogo/canchas}). */
public record CanchaResumenRespuesta(
        Long id,
        String slug,
        String nombre,
        String deporte,
        String deporteTexto,
        String superficie,
        Integer capacidad,
        boolean techada,
        boolean iluminacionLed,
        String descripcion,
        BigDecimal precioDia,
        BigDecimal precioNoche,
        String fotoPrincipal,
        CalificacionRespuesta calificacion
) {
}
