package pe.alcanfores.reservas.cliente;

import java.util.List;
import java.util.Map;

/** Consultas síncronas a catalogo-service (rutas {@code /interno/**}). Lanza {@code NegocioExcepcion} 503 si no responde. */
public interface CatalogoCliente {

    /** {@code GET /interno/configuracion} → mapa clave/valor ({@code adelanto_pct}, {@code dias_visibles}…). */
    Map<String, Object> configuracion();

    /** {@code GET /interno/canchas} → todas las canchas (activas e inactivas). */
    List<CanchaCatalogo> canchas();
}
