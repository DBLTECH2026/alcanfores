package pe.alcanfores.catalogo.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cálculo del promedio de estrellas con 1 decimal (HALF_UP): 4.25 → 4.3; sin reseñas → 0.0. */
public final class Calificaciones {

    private Calificaciones() {
    }

    public static BigDecimal promedio(long sumaPuntos, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(sumaPuntos).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
