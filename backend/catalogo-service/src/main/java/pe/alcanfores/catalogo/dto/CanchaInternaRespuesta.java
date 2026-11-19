package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;

/** Datos que necesitan reservas-service y pagos-service ({@code /interno/canchas}). */
public record CanchaInternaRespuesta(
        Long id,
        String slug,
        String nombre,
        String deporte,
        String deporteTexto,
        Integer capacidad,
        BigDecimal precioDia,
        BigDecimal precioNoche,
        String estado,
        Integer orden
) {
}
