package pe.alcanfores.pagos.cliente;

import java.util.Map;

/** Configuración del complejo en catalogo-service ({@code GET /interno/configuracion}). */
public interface CatalogoCliente {

    /** @return claves → valores, o un mapa vacío si catálogo no responde (se usan valores por defecto). */
    Map<String, String> configuracion();
}
