package pe.alcanfores.pagos.cliente;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.List;
import java.util.Optional;

/** Cliente de reservas-service por Eureka ({@code http://reservas-service}) con circuit breaker y timeout de 3 s. */
@Component
public class ReservasClienteHttp implements ReservasCliente {

    private static final Logger log = LoggerFactory.getLogger(ReservasClienteHttp.class);
    static final String CODIGO_NO_DISPONIBLE = "SERVICIO_NO_DISPONIBLE";
    static final String MENSAJE_NO_DISPONIBLE = "El servicio de reservas no responde. Intenta en unos segundos.";

    private final RestClient rest;
    private final CircuitBreaker circuito;

    public ReservasClienteHttp(@Qualifier("clienteBalanceado") RestClient.Builder balanceado,
                               CircuitBreakerFactory<?, ?> circuitos) {
        this.rest = balanceado.clone().baseUrl("http://reservas-service").build();
        this.circuito = circuitos.create("reservas-service");
    }

    @Override
    public Optional<ReservaInterna> buscar(String codigoReserva) {
        return circuito.run(() -> {
            try {
                return Optional.ofNullable(rest.get().uri("/interno/reservas/{codigo}", codigoReserva)
                        .retrieve().body(ReservaInterna.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.<ReservaInterna>empty();
            }
        }, this::caido);
    }

    @Override
    public List<ReservaSemilla> paraSemilla() {
        return circuito.run(() -> {
            List<ReservaSemilla> lista = rest.get().uri("/interno/reservas/para-semilla").retrieve()
                    .body(new ParameterizedTypeReference<List<ReservaSemilla>>() {
                    });
            return lista == null ? List.<ReservaSemilla>of() : lista;
        }, this::caido);
    }

    private <T> T caido(Throwable causa) {
        log.warn("reservas-service no responde: {}", causa.toString());
        throw NegocioExcepcion.noDisponible(CODIGO_NO_DISPONIBLE, MENSAJE_NO_DISPONIBLE);
    }
}
