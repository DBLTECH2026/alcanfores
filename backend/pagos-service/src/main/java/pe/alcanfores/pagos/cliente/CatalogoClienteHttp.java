package pe.alcanfores.pagos.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente de catalogo-service. Acepta la configuración como objeto {@code {clave: valor}} o como lista
 * {@code [{clave, valor}]}; si catálogo no responde devuelve un mapa vacío (el servicio usa valores por defecto).
 */
@Component
public class CatalogoClienteHttp implements CatalogoCliente {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClienteHttp.class);

    private final RestClient rest;
    private final CircuitBreaker circuito;

    public CatalogoClienteHttp(@Qualifier("clienteBalanceado") RestClient.Builder balanceado,
                               CircuitBreakerFactory<?, ?> circuitos) {
        this.rest = balanceado.clone().baseUrl("http://catalogo-service").build();
        this.circuito = circuitos.create("catalogo-service");
    }

    @Override
    public Map<String, String> configuracion() {
        return circuito.run(() -> aplanar(rest.get().uri("/interno/configuracion").retrieve().body(JsonNode.class)),
                causa -> {
                    log.warn("catalogo-service no responde; se usan valores por defecto: {}", causa.toString());
                    return Map.of();
                });
    }

    static Map<String, String> aplanar(JsonNode json) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (json == null) {
            return mapa;
        }
        if (json.isArray()) {
            json.forEach(n -> {
                if (n.hasNonNull("clave")) {
                    mapa.put(n.get("clave").asText(), n.path("valor").asText(""));
                }
            });
        } else {
            json.fields().forEachRemaining(e -> mapa.put(e.getKey(), e.getValue().isValueNode()
                    ? e.getValue().asText() : e.getValue().toString()));
        }
        return mapa;
    }
}
