package pe.alcanfores.reservas.cliente;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.reservas.config.ReservasPropiedades;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Cliente HTTP a catalogo-service por Eureka ({@code http://catalogo-service}) con timeout de 3 s y circuit breaker. */
@Component
public class CatalogoClienteHttp implements CatalogoCliente {

    private final RestClient http;
    private final CircuitBreaker circuito;

    public CatalogoClienteHttp(@LoadBalanced RestClient.Builder balanceado, CircuitBreakerFactory<?, ?> circuitos,
                               ReservasPropiedades propiedades) {
        this.http = balanceado.clone().baseUrl(propiedades.catalogoUrl()).build();
        this.circuito = circuitos.create("catalogo");
    }

    @Override
    public Map<String, Object> configuracion() {
        return circuito.run(() -> http.get().uri("/interno/configuracion").retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }), this::caido);
    }

    @Override
    public List<CanchaCatalogo> canchas() {
        return circuito.run(() -> http.get().uri("/interno/canchas").retrieve()
                .body(new ParameterizedTypeReference<List<CanchaCatalogo>>() { }), this::caido);
    }

    @Override
    public List<DiaEspecial> calendario(LocalDate desde, LocalDate hasta) {
        return circuito.run(() -> http.get().uri("/interno/calendario?desde={d}&hasta={h}", desde, hasta).retrieve()
                .body(new ParameterizedTypeReference<List<DiaEspecial>>() { }), this::caido);
    }

    @Override
    public boolean tieneResena(String codigoReserva) {
        return circuito.run(() -> {
            JsonNode r = http.get().uri("/interno/resenas/{codigo}", codigoReserva).retrieve().body(JsonNode.class);
            return r != null && r.path("existe").asBoolean(false);
        }, causa -> false);
    }

    private <T> T caido(Throwable causa) {
        throw NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de catálogo no responde. Intenta en unos segundos.");
    }
}
