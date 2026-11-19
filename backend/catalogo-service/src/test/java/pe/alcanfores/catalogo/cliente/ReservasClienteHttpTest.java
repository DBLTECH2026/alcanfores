package pe.alcanfores.catalogo.cliente;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Cliente de reservas contra un servidor HTTP simulado: elegible, 404, 422 con mensaje y caída (503). */
class ReservasClienteHttpTest {

    static final String URL = "http://reservas-service/interno/reservas/ALC-QW3RT7/elegible-resena?dni=45678912&idCancha=2";

    MockRestServiceServer servidor;
    ReservasClienteHttp cliente;

    /** Circuito sin estado: ejecuta y, si falla, aplica el fallback (como el de Resilience4j con el circuito cerrado). */
    static class CircuitoDirecto extends CircuitBreakerFactory<Object, ConfigBuilder<Object>> {
        @Override
        public CircuitBreaker create(String id) {
            return new CircuitBreaker() {
                @Override
                public <T> T run(Supplier<T> llamada, Function<Throwable, T> fallback) {
                    try {
                        return llamada.get();
                    } catch (RuntimeException e) {
                        return fallback.apply(e);
                    }
                }
            };
        }

        @Override
        protected ConfigBuilder<Object> configBuilder(String id) {
            return () -> null;
        }

        @Override
        public void configureDefault(Function<String, Object> configuracion) {
            // Sin configuración: el circuito de prueba no guarda estado.
        }
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservas-service");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ReservasClienteHttp(builder.build(), new CircuitoDirecto());
    }

    @Test
    void elegible() {
        servidor.expect(requestTo(URL)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"elegible\":true,\"motivo\":null,\"autor\":\"Diego R.\"}", MediaType.APPLICATION_JSON));
        ElegibilidadResena r = cliente.elegibleResena("ALC-QW3RT7", "45678912", 2L);
        assertThat(r.elegible()).isTrue();
        assertThat(r.autor()).isEqualTo("Diego R.");
        servidor.verify();
    }

    @Test
    void reservaInexistenteEsNoElegible() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        ElegibilidadResena r = cliente.elegibleResena("ALC-QW3RT7", "45678912", 2L);
        assertThat(r.elegible()).isFalse();
        assertThat(r.motivo()).isEqualTo(ReservasClienteHttp.MOTIVO_NO_ENCONTRADA);
    }

    @Test
    void errorDeNegocioUsaElMensajeDeReservas() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"codigo\":\"X\",\"mensaje\":\"La reserva fue cancelada.\"}"));
        assertThat(cliente.elegibleResena("ALC-QW3RT7", "45678912", 2L).motivo()).isEqualTo("La reserva fue cancelada.");
    }

    @Test
    void errorSinCuerpoUsaMensajeGenerico() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("<html>"));
        assertThat(cliente.elegibleResena("ALC-QW3RT7", "45678912", 2L).motivo())
                .isEqualTo("No pudimos verificar tu reserva. Revisa el código y el DNI.");
    }

    @Test
    void caidaDeReservasEs503() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> cliente.elegibleResena("ALC-QW3RT7", "45678912", 2L))
                .isInstanceOf(NegocioExcepcion.class)
                .hasMessage(ReservasClienteHttp.MENSAJE_CAIDO)
                .satisfies(e -> assertThat(((NegocioExcepcion) e).getStatus()).isEqualTo(503));
    }
}
