package pe.alcanfores.pagos.cliente;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.config.PagosPropiedades;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Clientes REST con MockRestServiceServer: reservas, catálogo y Mercado Pago (sin red). */
class ClientesHttpTest {

    /** Circuit breaker que solo ejecuta y deriva los errores al fallback (sin estado). */
    @SuppressWarnings("unchecked")
    static CircuitBreakerFactory<?, ?> circuitoDirecto() {
        CircuitBreakerFactory<?, ?> fabrica = mock(CircuitBreakerFactory.class);
        when(fabrica.create(anyString())).thenReturn(new CircuitBreaker() {
            @Override
            public <T> T run(Supplier<T> ejecutar, Function<Throwable, T> respaldo) {
                try {
                    return ejecutar.get();
                } catch (RuntimeException e) {
                    return respaldo.apply(e);
                }
            }
        });
        return fabrica;
    }

    @Test
    void reservasDevuelveLaReservaVacioSi404Y503SiCae() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        ReservasClienteHttp cliente = new ReservasClienteHttp(builder, circuitoDirecto());

        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P")).andRespond(withSuccess("""
                {"idReserva":5,"codigoReserva":"ALC-7K3M9P","nombreCancha":"Cancha 2","deporte":"FUTBOL_6",
                 "fecha":"2026-11-20","horaInicio":"19:00:00","horaFin":"20:00","montoTotal":90.00,"montoAdelanto":45.00,
                 "montoPagado":0,"estado":"PENDIENTE","estadoPago":"SIN_PAGO","saldo":90.00,"inicio":"2026-11-20T19:00:00",
                 "cliente":{"dni":"70123321","nombres":"Diego","apellidos":"Ramos"},"campoNuevo":"se ignora"}
                """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-ZZZZZZ")).andRespond(withResourceNotFound());
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-CAIDAA")).andRespond(withServerError());
        servidor.expect(requestTo("http://reservas-service/interno/reservas/para-semilla"))
                .andRespond(withSuccess("[{\"codigoReserva\":\"ALC-7K3M9P\",\"estadoPago\":\"PAGADO_TOTAL\"}]", MediaType.APPLICATION_JSON));

        ReservaInterna r = cliente.buscar("ALC-7K3M9P").orElseThrow();
        assertThat(r.dniCliente()).isEqualTo("70123321");
        assertThat(r.saldoPendiente()).isEqualByComparingTo("90.00");
        assertThat(cliente.buscar("ALC-ZZZZZZ")).isEmpty();
        assertThatThrownBy(() -> cliente.buscar("ALC-CAIDAA")).isInstanceOfSatisfying(NegocioExcepcion.class, e -> {
            assertThat(e.getStatus()).isEqualTo(503);
            assertThat(e.getCodigo()).isEqualTo("SERVICIO_NO_DISPONIBLE");
        });
        assertThat(cliente.paraSemilla()).hasSize(1);
        servidor.verify();
    }

    @Test
    void catalogoAceptaMapaOListaYSiCaeDevuelveVacio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CatalogoClienteHttp cliente = new CatalogoClienteHttp(builder, circuitoDirecto());
        servidor.expect(requestTo("http://catalogo-service/interno/configuracion"))
                .andRespond(withSuccess("{\"yape_numero\":\"987654321\",\"adelanto_pct\":50}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://catalogo-service/interno/configuracion"))
                .andRespond(withSuccess("[{\"clave\":\"plin_numero\",\"valor\":\"912345678\"}]", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://catalogo-service/interno/configuracion")).andRespond(withServerError());

        assertThat(cliente.configuracion()).containsEntry("yape_numero", "987654321").containsEntry("adelanto_pct", "50");
        assertThat(cliente.configuracion()).containsEntry("plin_numero", "912345678");
        assertThat(cliente.configuracion()).isEmpty();
    }

    static MercadoPagoClienteHttp mercadoPago(RestClient.Builder builder) {
        PagosPropiedades p = new PagosPropiedades();
        p.getMercadopago().setAccessToken("TEST-123");
        return new MercadoPagoClienteHttp(builder, p);
    }

    @Test
    void mercadoPagoCreaPreferenciaConTokenEIdempotencia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-123"))
                .andExpect(header("X-Idempotency-Key", "ALP-ABCDEFGHJK"))
                .andExpect(content().json("{\"external_reference\":\"ALP-ABCDEFGHJK\"}"))
                .andRespond(withSuccess("{\"id\":\"pref-9\",\"init_point\":\"https://mp/i\",\"sandbox_init_point\":\"https://mp/s\"}",
                        MediaType.APPLICATION_JSON));
        MercadoPagoCliente.PreferenciaMp p = mercadoPago(builder).crearPreferencia(
                Map.of("external_reference", "ALP-ABCDEFGHJK"), "ALP-ABCDEFGHJK");
        assertThat(p.id()).isEqualTo("pref-9");
        assertThat(p.sandboxInitPoint()).isEqualTo("https://mp/s");
        servidor.verify();
    }

    @Test
    void mercadoPagoConsultaPagosYBuscaPorReferencia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/123")).andRespond(withSuccess("""
                {"id":123,"status":"approved","status_detail":"accredited","external_reference":"ALP-ABCDEFGHJK",
                 "transaction_amount":45.0,"payment_method_id":"visa","payment_type_id":"credit_card",
                 "authorization_code":"301299","card":{"last_four_digits":"3704"}}
                """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/999")).andRespond(withResourceNotFound());
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/search?external_reference=ALP-ABCDEFGHJK&sort=date_created&criteria=desc"))
                .andRespond(withSuccess("{\"results\":[{\"id\":7,\"status\":\"rejected\",\"status_detail\":\"cc_rejected_other_reason\"}]}",
                        MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/500")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        MercadoPagoClienteHttp cliente = mercadoPago(builder);
        MercadoPagoCliente.PagoMp pago = cliente.obtenerPago("123").orElseThrow();
        assertThat(pago.aprobado()).isTrue();
        assertThat(pago.monto()).isEqualByComparingTo(new BigDecimal("45.0"));
        assertThat(pago.ultimos4()).isEqualTo("3704");
        assertThat(cliente.obtenerPago("999")).isEmpty();
        assertThat(cliente.buscarPorReferencia("ALP-ABCDEFGHJK")).singleElement()
                .satisfies(p -> assertThat(p.rechazado()).isTrue());
        assertThatThrownBy(() -> cliente.obtenerPago("500")).isInstanceOfSatisfying(NegocioExcepcion.class,
                e -> assertThat(e.getCodigo()).isEqualTo("MP_NO_DISPONIBLE"));
        servidor.verify();
    }
}
