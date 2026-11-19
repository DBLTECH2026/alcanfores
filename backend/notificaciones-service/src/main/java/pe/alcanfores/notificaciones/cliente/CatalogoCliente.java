package pe.alcanfores.notificaciones.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/** Cliente de catalogo-service: configuración del complejo ({@code GET /interno/configuracion}). */
@Component
public class CatalogoCliente {

    static final String URL_CONFIGURACION = "http://catalogo-service/interno/configuracion";

    private final RestClient consultas;
    private final LlamadaProtegida protegida;

    public CatalogoCliente(@Qualifier("restInterno") RestClient consultas, LlamadaProtegida protegida) {
        this.consultas = consultas;
        this.protegida = protegida;
    }

    /**
     * Claves de configuración como texto ({@code recordatorio_horas → "2"}, {@code direccion → "..."}). Acepta un
     * objeto {@code {clave: valor}} o una lista {@code [{clave, valor}]}.
     */
    public Map<String, String> configuracion() {
        JsonNode nodo = protegida.ejecutar("catalogo-service", "catálogo", () -> consultas.get()
                .uri(URL_CONFIGURACION)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        Map<String, String> claves = new HashMap<>();
        if (nodo == null) {
            return claves;
        }
        if (nodo.isArray()) {
            nodo.forEach(item -> {
                if (item.hasNonNull("clave") && item.hasNonNull("valor")) {
                    claves.put(item.get("clave").asText(), item.get("valor").asText());
                }
            });
        } else {
            nodo.fields().forEachRemaining(e -> {
                if (!e.getValue().isNull() && e.getValue().isValueNode()) {
                    claves.put(e.getKey(), e.getValue().asText());
                }
            });
        }
        return claves;
    }
}
