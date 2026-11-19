package pe.alcanfores.auth.cliente;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.Canal;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.InterceptorInterno;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Envío interno síncrono a notificaciones-service con MockRestServiceServer (token interno y JSON exactos). */
class NotificacionesClienteTest {

    private MockRestServiceServer servidor;
    private NotificacionesCliente cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder b = RestClient.builder().requestInterceptor(new InterceptorInterno("token-de-prueba"));
        servidor = MockRestServiceServer.bindTo(b).build();
        cliente = new NotificacionesCliente(b.build());
    }

    @Test
    void enviaElCodigoConElTokenInternoYLeeElResultado() {
        servidor.expect(requestTo("http://notificaciones-service/interno/codigos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Interno-Token", "token-de-prueba"))
                .andExpect(content().json("{\"canal\":\"WHATSAPP\",\"destino\":\"987654321\",\"nombre\":\"Lucía Mendoza\","
                        + "\"codigo\":\"482913\",\"motivo\":\"RECUPERAR_CONTRASENA\"}", true))
                .andRespond(withSuccess("{\"estado\":\"SIMULADO\",\"simulado\":true,\"detalle\":null}", MediaType.APPLICATION_JSON));

        NotificacionesCliente.Resultado r = cliente.enviar(new NotificacionesCliente.Solicitud(Canal.WHATSAPP, "987654321",
                "Lucía Mendoza", "482913", NotificacionesCliente.RECUPERAR));

        assertThat(r.simulado()).isTrue();
        assertThat(r.fallido()).isFalse();
        assertThat(new NotificacionesCliente.Solicitud(Canal.CORREO, "a@b.pe", null, "482913", "X").toString())
                .doesNotContain("482913");
        servidor.verify();
    }

    @Test
    void errorOTiempoAgotadoSeTraducenA503() {
        servidor.expect(requestTo("http://notificaciones-service/interno/codigos")).andRespond(withServerError());
        servidor.expect(requestTo("http://notificaciones-service/interno/codigos"))
                .andRespond(withException(new SocketTimeoutException("lento")));
        NotificacionesCliente.Solicitud s = new NotificacionesCliente.Solicitud(Canal.CORREO, "a@b.pe", "Ana", null,
                NotificacionesCliente.CAMBIADA);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cliente.enviar(s)).isInstanceOf(NegocioExcepcion.class)
                    .hasMessage("El servicio de notificaciones no responde. Intenta en unos segundos.")
                    .extracting("status").isEqualTo(503);
        }
    }
}
