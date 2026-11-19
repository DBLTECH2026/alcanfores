package pe.alcanfores.reservas.cliente;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Consultas síncronas a catalogo-service (rutas {@code /interno/**}). Lanza {@code NegocioExcepcion} 503 si no responde. */
public interface CatalogoCliente {

    /** {@code GET /interno/configuracion} → mapa clave/valor ({@code adelanto_pct}, {@code dias_visibles}…). */
    Map<String, Object> configuracion();

    /** {@code GET /interno/canchas} → todas las canchas (activas e inactivas). */
    List<CanchaCatalogo> canchas();

    /** {@code GET /interno/calendario?desde&hasta} → feriados y días no laborables del rango. */
    List<DiaEspecial> calendario(LocalDate desde, LocalDate hasta);

    /**
     * {@code GET /interno/resenas/{codigo}} → ¿la reserva ya tiene reseña? Si catálogo no responde devuelve
     * {@code false} (no lanza): la bandera {@code puedeResenar} no debe tumbar la consulta de la reserva.
     */
    boolean tieneResena(String codigoReserva);
}
