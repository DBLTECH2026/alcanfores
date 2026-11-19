package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Promedio (1 decimal), total y distribución {"5": n, …, "1": n} de las reseñas publicadas. */
public record ResumenResenasRespuesta(BigDecimal promedio, long total, Map<String, Long> distribucion) {
}
