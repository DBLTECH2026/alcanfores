package pe.alcanfores.reservas.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.reservas.config.ReservasPropiedades;

import java.io.IOException;

/** Cliente HTTP a pagos-service por Eureka ({@code http://pagos-service}) con timeout de 3 s y circuit breaker. */
@Component
public class PagosClienteHttp implements PagosCliente {

    private final RestClient http;
    private final CircuitBreaker circuito;
    private final ObjectMapper json;

    public PagosClienteHttp(@LoadBalanced RestClient.Builder balanceado, CircuitBreakerFactory<?, ?> circuitos,
                            ReservasPropiedades propiedades, ObjectMapper json) {
        this.http = balanceado.clone().baseUrl(propiedades.pagosUrl()).build();
        this.circuito = circuitos.create("pagos");
        this.json = json;
    }

    @Override
    public void registrarPagoCaja(PagoCaja pago) {
        circuito.run(() -> {
            http.post().uri("/interno/pagos/caja").contentType(MediaType.APPLICATION_JSON).body(pago).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (solicitud, respuesta) -> {
                        throw NegocioExcepcion.regla("PAGO_NO_REGISTRADO", mensajeDe(respuesta.getBody().readAllBytes()));
                    })
                    .toBodilessEntity();
            return Boolean.TRUE;
        }, causa -> {
            if (causa instanceof NegocioExcepcion ne) {
                throw ne;
            }
            throw NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                    "El servicio de pagos no responde. Intenta en unos segundos.");
        });
    }

    private String mensajeDe(byte[] cuerpo) {
        try {
            JsonNode nodo = json.readTree(cuerpo);
            String mensaje = nodo.path("mensaje").asText("");
            return mensaje.isBlank() ? "pagos-service rechazó el cobro." : mensaje;
        } catch (IOException e) {
            return "pagos-service rechazó el cobro.";
        }
    }
}
