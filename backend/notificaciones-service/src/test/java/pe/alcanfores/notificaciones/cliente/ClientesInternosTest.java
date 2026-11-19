package pe.alcanfores.notificaciones.cliente;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.notificaciones.Datos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Clientes de reservas, pagos y catálogo con MockRestServiceServer y un circuit breaker de paso directo. */
class ClientesInternosTest {

    private MockRestServiceServer servidor;
    private RestClient rest;
    private LlamadaProtegida protegida;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Circuit breaker que ejecuta la llamada y, si falla, el fallback (como el real con el circuito cerrado). */
    static final class PasoDirecto implements CircuitBreaker {
        @Override
        public <T> T run(Supplier<T> llamada, Function<Throwable, T> fallback) {
            try {
                return llamada.get();
            } catch (RuntimeException e) {
                return fallback.apply(e);
            }
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        RestClient.Builder b = RestClient.builder().defaultHeader("X-Interno-Token", "token");
        servidor = MockRestServiceServer.bindTo(b).build();
        rest = b.build();
        CircuitBreakerFactory<?, ?> f = mock(CircuitBreakerFactory.class);
        when(f.create(anyString())).thenReturn(new PasoDirecto());
        protegida = new LlamadaProtegida(f);
    }

    @Test
    @DisplayName("Constancia PDF desde reservas-service con el token interno")
    void constancia() {
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P/constancia.pdf"))
                .andExpect(header("X-Interno-Token", "token"))
                .andRespond(withSuccess(Datos.PDF, MediaType.APPLICATION_PDF));

        assertThat(new ReservasCliente(rest, rest, protegida, json).constanciaPdf(Datos.CODIGO)).isEqualTo(Datos.PDF);
    }

    @Test
    @DisplayName("404 → NO_ENCONTRADO; 500 → SERVICIO_NO_DISPONIBLE (503)")
    void errores() {
        ReservasCliente cliente = new ReservasCliente(rest, rest, protegida, json);
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P/constancia.pdf"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> cliente.constanciaPdf(Datos.CODIGO))
                .isInstanceOf(NegocioExcepcion.class).extracting("status").isEqualTo(404);

        servidor.reset();
        servidor.expect(requestTo("http://pagos-service/interno/comprobantes/33/pdf"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> new PagosCliente(rest, protegida).comprobantePdf(33L))
                .isInstanceOf(NegocioExcepcion.class)
                .hasMessage("El servicio de pagos no responde. Intenta en unos segundos.")
                .extracting("codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    @DisplayName("PDF del comprobante desde pagos-service")
    void comprobante() {
        servidor.expect(requestTo("http://pagos-service/interno/comprobantes/33/pdf"))
                .andRespond(withSuccess(Datos.PDF, MediaType.APPLICATION_PDF));

        assertThat(new PagosCliente(rest, protegida).comprobantePdf(33L)).isEqualTo(Datos.PDF);
    }

    @Test
    @DisplayName("Reserva interna en formato plano (DatosReserva + estado, saldo, venceEn)")
    void reservaPlana() {
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P"))
                .andRespond(withSuccess("""
                        {"idReserva":10,"codigoReserva":"ALC-7K3M9P","idCancha":1,"nombreCancha":"Cancha 1","deporte":"FUTBOL_5",
                         "fecha":"2026-11-20","horaInicio":"19:00","horaFin":"20:00","montoTotal":80.00,"montoAdelanto":40.00,
                         "montoPagado":40.00,"cliente":{"dni":"70123321","nombres":"Diego","apellidos":"Ramos","telefono":"987654321",
                         "correo":"d@x.pe"},"estado":"CONFIRMADA","estadoPago":"ADELANTO_PAGADO","saldo":40.00,
                         "venceEn":"2026-11-19T22:30:00","inicio":"2026-11-20T19:00:00"}""", MediaType.APPLICATION_JSON));

        ReservaInterna r = new ReservasCliente(rest, rest, protegida, json).consultar(Datos.CODIGO);

        assertThat(r.datos().cliente().correo()).isEqualTo("d@x.pe");
        assertThat(r.datos().horaInicio()).hasToString("19:00");
        // reservas v1 no manda horarioTexto ni cantidadTurnos
        assertThat(r.datos().horarioTexto()).isNull();
        assertThat(r.datos().cantidadTurnos()).isZero();
        assertThat(r.estado()).isEqualTo("CONFIRMADA");
        assertThat(r.saldo()).isEqualByComparingTo("40.00");
        assertThat(r.venceEn()).isEqualTo(LocalDateTime.of(2026, 11, 19, 22, 30));
        assertThat(r.cancelada()).isFalse();
    }

    @Test
    @DisplayName("Reserva interna en formato anidado ({reserva: {...}, estado})")
    void reservaAnidada() {
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P"))
                .andRespond(withSuccess("{\"reserva\":{\"codigoReserva\":\"ALC-7K3M9P\",\"fecha\":\"2026-11-20\"},"
                        + "\"estado\":\"CANCELADA\"}", MediaType.APPLICATION_JSON));

        ReservaInterna r = new ReservasCliente(rest, rest, protegida, json).consultar(Datos.CODIGO);

        assertThat(r.datos().codigoReserva()).isEqualTo(Datos.CODIGO);
        assertThat(r.cancelada()).isTrue();
        assertThat(r.saldo()).isNull();
    }

    @Test
    @DisplayName("Respuesta ilegible de reservas → 503")
    void reservaIlegible() {
        servidor.expect(requestTo("http://reservas-service/interno/reservas/ALC-7K3M9P"))
                .andRespond(withSuccess("{\"fecha\":\"no-es-fecha\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ReservasCliente(rest, rest, protegida, json).consultar(Datos.CODIGO))
                .isInstanceOf(NegocioExcepcion.class).extracting("status").isEqualTo(503);
    }

    @Test
    @DisplayName("Configuración del catálogo como objeto {clave: valor} o lista [{clave, valor}]")
    void configuracion() {
        CatalogoCliente cliente = new CatalogoCliente(rest, protegida);
        servidor.expect(requestTo("http://catalogo-service/interno/configuracion"))
                .andRespond(withSuccess("{\"recordatorio_horas\":3,\"direccion\":\"Av. Prueba\",\"lista\":[1],\"nulo\":null}",
                        MediaType.APPLICATION_JSON));
        assertThat(cliente.configuracion()).containsEntry("recordatorio_horas", "3").containsEntry("direccion", "Av. Prueba")
                .doesNotContainKeys("lista", "nulo");

        servidor.reset();
        servidor.expect(requestTo("http://catalogo-service/interno/configuracion"))
                .andRespond(withSuccess("[{\"clave\":\"whatsapp\",\"valor\":\"914995187\"},{\"clave\":\"x\"}]",
                        MediaType.APPLICATION_JSON));
        assertThat(cliente.configuracion()).containsExactlyEntriesOf(Map.of("whatsapp", "914995187"));
    }

    @Test
    @DisplayName("Traducción de errores: respeta NegocioExcepcion y busca el 404 en la causa")
    void traduccion() {
        NegocioExcepcion propia = NegocioExcepcion.regla("X", "y");
        assertThat(LlamadaProtegida.traducir("s", "reservas", propia)).isSameAs(propia);
        assertThat(LlamadaProtegida.traducir("s", "reservas", new IllegalStateException("caído")).getStatus()).isEqualTo(503);
    }
}
