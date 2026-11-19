package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.ComplejoRespuesta;

import java.util.Map;

public interface ConfiguracionServicio {

    /** Todas las claves {clave: valor}; si alguna falta en la BD se usa su valor por defecto. */
    Map<String, String> todas();

    /** Actualiza las claves enviadas (validando cada una y las reglas entre ellas) y devuelve todas. */
    Map<String, String> actualizar(Map<String, Object> cambios);

    /** Datos públicos del complejo y sus reglas para el portal. */
    ComplejoRespuesta complejo();
}
