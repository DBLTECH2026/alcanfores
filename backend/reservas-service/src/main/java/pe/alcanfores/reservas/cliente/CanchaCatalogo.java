package pe.alcanfores.reservas.cliente;

import pe.alcanfores.reservas.modelo.Franja;

import java.math.BigDecimal;
import java.util.List;

/** Cancha tal como la publica catalogo-service ({@code GET /interno/canchas} o el evento {@code cancha.actualizada}). */
public record CanchaCatalogo(Long id, String nombre, String deporte, BigDecimal precioDia, BigDecimal precioNoche,
                             String estado) {

    public static final String ACTIVA = "ACTIVA";

    /** Las 4 canchas semilla del SPEC §5.2 (si catálogo no responde al arrancar). */
    public static List<CanchaCatalogo> semilla() {
        return List.of(
                new CanchaCatalogo(1L, "Cancha 1 · Fútbol 5", "FUTBOL_5", new BigDecimal("60.00"), new BigDecimal("80.00"), ACTIVA),
                new CanchaCatalogo(2L, "Cancha 2 · Fútbol 6", "FUTBOL_6", new BigDecimal("70.00"), new BigDecimal("90.00"), ACTIVA),
                new CanchaCatalogo(3L, "Cancha 3 · Fútbol 7", "FUTBOL_7", new BigDecimal("90.00"), new BigDecimal("120.00"), ACTIVA),
                new CanchaCatalogo(4L, "Losa multiusos", "MULTIUSO", new BigDecimal("40.00"), new BigDecimal("50.00"), ACTIVA));
    }

    public boolean activa() {
        return estado == null || ACTIVA.equalsIgnoreCase(estado);
    }

    public BigDecimal precio(Franja franja) {
        BigDecimal p = franja == Franja.NOCHE ? precioNoche : precioDia;
        return p.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
