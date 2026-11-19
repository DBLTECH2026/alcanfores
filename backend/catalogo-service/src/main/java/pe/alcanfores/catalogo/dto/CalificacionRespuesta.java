package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;

/** Promedio (1 decimal) y total de reseñas publicadas. */
public record CalificacionRespuesta(BigDecimal promedio, long total) {
}
