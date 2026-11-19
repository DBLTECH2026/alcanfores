package pe.alcanfores.notificaciones.cliente;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvolutionClienteTest {

    static final String URL = "https://evolution.prueba.pe";
    private MockRestServiceServer servidor;
    private EvolutionCliente cliente;

    static NotificacionesPropiedades propiedades() {
        NotificacionesPropiedades p = new NotificacionesPropiedades();
        p.getEvolution().setUrl(URL + "/");
        p.getEvolution().setApikey("clave-secreta");
        return p;
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new EvolutionCliente(builder, propiedades());
    }

    @Test
    @DisplayName("sendText: POST /message/sendText/{instancia} con apikey, number y text")
    void enviarTexto() {
        servidor.expect(requestTo(URL + "/message/sendText/alcanfores"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "clave-secreta"))
                .andExpect(jsonPath("$.number").value("51987654321"))
                .andExpect(jsonPath("$.text").value("*Hola*"))
                .andRespond(withSuccess("{\"key\":{\"id\":\"BAE5ABC\"},\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        assertThat(cliente.enviarTexto("51987654321", "*Hola*")).isEqualTo("BAE5ABC");
        servidor.verify();
    }

    @Test
    @DisplayName("sendMedia: documento PDF en base64 con fileName y caption")
    void enviarDocumento() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        servidor.expect(requestTo(URL + "/message/sendMedia/alcanfores"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "clave-secreta"))
                .andExpect(jsonPath("$.number").value("51987654321"))
                .andExpect(jsonPath("$.mediatype").value("document"))
                .andExpect(jsonPath("$.mimetype").value("application/pdf"))
                .andExpect(jsonPath("$.media").value(Base64.getEncoder().encodeToString(pdf)))
                .andExpect(jsonPath("$.fileName").value("constancia-ALC-7K3M9P.pdf"))
                .andExpect(jsonPath("$.caption").value("Tu constancia"))
                .andRespond(withSuccess("{\"key\":{\"id\":\"BAE5DOC\"}}", MediaType.APPLICATION_JSON));

        assertThat(cliente.enviarDocumento("51987654321", pdf, "constancia-ALC-7K3M9P.pdf", "Tu constancia"))
                .isEqualTo("BAE5DOC");
        servidor.verify();
    }

    @Test
    @DisplayName("Errores de Evolution se traducen a mensajes en español con el código HTTP")
    void errores() {
        servidor.expect(requestTo(URL + "/message/sendText/alcanfores")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> cliente.enviarTexto("51987654321", "x"))
                .isInstanceOf(EvolutionExcepcion.class)
                .hasMessageContaining("EVOLUTION_APIKEY")
                .extracting("status").isEqualTo(401);

        servidor.reset();
        servidor.expect(requestTo(URL + "/message/sendText/alcanfores")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"response\":{\"message\":[{\"exists\":false,\"number\":\"51900000000\"}]}}"));
        assertThatThrownBy(() -> cliente.enviarTexto("51900000000", "x")).hasMessageContaining("no tiene WhatsApp");

        servidor.reset();
        servidor.expect(requestTo(URL + "/message/sendText/alcanfores")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Connection Closed"));
        assertThatThrownBy(() -> cliente.enviarTexto("51987654321", "x")).hasMessageContaining("500")
                .hasMessageContaining("Connection Closed");
    }

    @Test
    @DisplayName("connectionState open + número vinculado (fetchInstances)")
    void estadoConectado() {
        servidor.expect(requestTo(URL + "/instance/connectionState/alcanfores"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "clave-secreta"))
                .andRespond(withSuccess("{\"instance\":{\"instanceName\":\"alcanfores\",\"state\":\"open\"}}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/instance/fetchInstances?instanceName=alcanfores"))
                .andRespond(withSuccess("[{\"name\":\"alcanfores\",\"ownerJid\":\"51914995187@s.whatsapp.net\"}]",
                        MediaType.APPLICATION_JSON));

        EvolutionCliente.EstadoConexion e = cliente.estado();

        assertThat(e.estado()).isEqualTo("open");
        assertThat(e.numero()).isEqualTo("51914995187");
        servidor.verify();
    }

    @Test
    @DisplayName("connectionState de una instancia que no existe → close")
    void estadoSinInstancia() {
        servidor.expect(requestTo(URL + "/instance/connectionState/alcanfores")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(cliente.estado()).isEqualTo(new EvolutionCliente.EstadoConexion("close", null));
    }

    @Test
    @DisplayName("connect devuelve el QR en base64 y el código de emparejamiento")
    void conectar() {
        servidor.expect(requestTo(URL + "/instance/connect/alcanfores?number=51914995187"))
                .andRespond(withSuccess("{\"pairingCode\":\"WZYEH1YY\",\"code\":\"2@abc\",\"base64\":\"data:image/png;base64,iVBOR\",\"count\":1}",
                        MediaType.APPLICATION_JSON));

        EvolutionCliente.CodigoConexion c = cliente.conectar("51914995187");

        assertThat(c.qrBase64()).isEqualTo("data:image/png;base64,iVBOR");
        assertThat(c.codigoEmparejamiento()).isEqualTo("WZYEH1YY");
        assertThat(c.estado()).isEqualTo("connecting");
    }

    @Test
    @DisplayName("connect sobre una instancia inexistente la crea con WHATSAPP-BAILEYS y qrcode:true")
    void conectarCreaInstancia() {
        servidor.expect(requestTo(URL + "/instance/connect/alcanfores")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        servidor.expect(requestTo(URL + "/instance/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.instanceName").value("alcanfores"))
                .andExpect(jsonPath("$.integration").value("WHATSAPP-BAILEYS"))
                .andExpect(jsonPath("$.qrcode").value(true))
                .andRespond(withSuccess("{\"instance\":{\"instanceName\":\"alcanfores\",\"status\":\"created\"},"
                        + "\"qrcode\":{\"pairingCode\":null,\"code\":\"2@x\",\"base64\":\"data:image/png;base64,QR\"}}",
                        MediaType.APPLICATION_JSON));

        EvolutionCliente.CodigoConexion c = cliente.conectar(null);

        assertThat(c.qrBase64()).isEqualTo("data:image/png;base64,QR");
        assertThat(c.codigoEmparejamiento()).isNull();
        servidor.verify();
    }

    @Test
    @DisplayName("connect sobre una instancia ya conectada → open sin QR")
    void conectarYaConectado() {
        servidor.expect(requestTo(URL + "/instance/connect/alcanfores"))
                .andRespond(withSuccess("{\"instance\":{\"instanceName\":\"alcanfores\",\"state\":\"open\"}}", MediaType.APPLICATION_JSON));

        assertThat(cliente.conectar(null)).isEqualTo(new EvolutionCliente.CodigoConexion(null, null, "open"));
    }

    @Test
    @DisplayName("logout: DELETE /instance/logout; si ya estaba desconectado (400) no es error")
    void desconectar() {
        servidor.expect(requestTo(URL + "/instance/logout/alcanfores")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));
        cliente.desconectar();
        servidor.verify();

        servidor.reset();
        servidor.expect(requestTo(URL + "/instance/logout/alcanfores")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        cliente.desconectar();

        servidor.reset();
        servidor.expect(requestTo(URL + "/instance/logout/alcanfores")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(cliente::desconectar).isInstanceOf(EvolutionExcepcion.class);
    }

    @Test
    @DisplayName("Sin URL o sin apikey el cliente no está configurado")
    void configuracion() {
        NotificacionesPropiedades p = new NotificacionesPropiedades();
        assertThat(new EvolutionCliente(RestClient.builder(), p).configurado()).isFalse();
        p.getEvolution().setUrl(URL);
        assertThat(new EvolutionCliente(RestClient.builder(), p).configurado()).isFalse();
        p.getEvolution().setApikey("x");
        assertThat(new EvolutionCliente(RestClient.builder(), p).configurado()).isTrue();
        assertThat(cliente.instancia()).isEqualTo("alcanfores");
    }

    @Test
    @DisplayName("Sin conexión con Evolution: mensaje claro y estado 0")
    void sinConexion() {
        NotificacionesPropiedades p = new NotificacionesPropiedades();
        p.getEvolution().setUrl("http://127.0.0.1:1");
        p.getEvolution().setApikey("x");
        EvolutionCliente sinServidor = new EvolutionCliente(RestClient.builder(), p);

        assertThatThrownBy(() -> sinServidor.enviarTexto("51987654321", "hola"))
                .isInstanceOf(EvolutionExcepcion.class)
                .hasMessageContaining("No se pudo conectar con Evolution API")
                .extracting("status").isEqualTo(0);
    }
}
