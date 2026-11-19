package pe.alcanfores.catalogo.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Cliente HTTP de reservas-service con circuit breaker. Un 4xx de reservas (reserva inexistente, DNI que no calza…)
 * no es una falla del servicio: se traduce a «no elegible» con el mensaje que mandó reservas. Un 5xx, timeout o
 * circuito abierto termina en 503 {@code SERVICIO_NO_DISPONIBLE}.
 */
@Component
public class ReservasClienteHttp implements ReservasCliente {

    static final String CIRCUITO = "reservas";
    static final String MOTIVO_NO_ENCONTRADA = "No encontramos una reserva con ese código y DNI.";
    static final String MENSAJE_CAIDO = "El servicio de reservas no responde. Intenta en unos segundos.";

    private static final Logger log = LoggerFactory.getLogger(ReservasClienteHttp.class);

    private final RestClient rest;
    private final CircuitBreakerFactory<?, ?> circuitos;

    public ReservasClienteHttp(@Qualifier("clienteReservas") RestClient rest, CircuitBreakerFactory<?, ?> circuitos) {
        this.rest = rest;
        this.circuitos = circuitos;
    }

    @Override
    public ElegibilidadResena elegibleResena(String codigoReserva, String dni, Long idCancha) {
        return ejecutar(() -> rest.get()
                .uri("/interno/reservas/{codigo}/elegible-resena?dni={dni}&idCancha={idCancha}", codigoReserva, dni, idCancha)
                .exchange((solicitud, respuesta) -> {
                    int estado = respuesta.getStatusCode().value();
                    if (estado >= 500) {
                        throw new IllegalStateException("reservas-service respondió " + estado);
                    }
                    if (estado == 404) {
                        return new ElegibilidadResena(false, MOTIVO_NO_ENCONTRADA, null);
                    }
                    if (estado >= 400) {
                        JsonNode cuerpo;
                        try {
                            cuerpo = respuesta.bodyTo(JsonNode.class);
                        } catch (RuntimeException e) {
                            cuerpo = null;
                        }
                        return new ElegibilidadResena(false, motivoDe(cuerpo), null);
                    }
                    ElegibilidadResena r = respuesta.bodyTo(ElegibilidadResena.class);
                    if (r == null) {
                        throw new IllegalStateException("reservas-service respondió sin cuerpo");
                    }
                    return r;
                }));
    }

    /** Corre la llamada dentro del circuito conservando el X-Request-Id (MDC) del hilo original. */
    private <T> T ejecutar(Supplier<T> llamada) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return circuitos.create(CIRCUITO).run(() -> {
            if (contexto != null) {
                MDC.setContextMap(contexto);
            }
            try {
                return llamada.get();
            } finally {
                MDC.clear();
            }
        }, error -> {
            log.warn("reservas-service no disponible: {}", error.toString());
            throw NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", MENSAJE_CAIDO);
        });
    }

    private static String motivoDe(JsonNode cuerpo) {
        if (cuerpo != null) {
            for (String campo : new String[]{"motivo", "mensaje"}) {
                JsonNode valor = cuerpo.get(campo);
                if (valor != null && valor.isTextual() && !valor.asText().isBlank()) {
                    return valor.asText();
                }
            }
        }
        return "No pudimos verificar tu reserva. Revisa el código y el DNI.";
    }
}
