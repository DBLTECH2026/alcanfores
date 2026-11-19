package pe.alcanfores.reservas.cliente;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.reservas.config.ClientesHttpConfig;
import pe.alcanfores.reservas.config.ReservasPropiedades;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Proveedor Factiliza simulado con MockRestServiceServer. */
class FactilizaProveedorTest {

    private static final String URL = "https://api.factiliza.com/v1";
    private MockRestServiceServer servidor;
    private FactilizaProveedor proveedor;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        proveedor = new FactilizaProveedor(builder, new ReservasPropiedades.Factiliza("token-prueba", URL));
    }

    @Test
    void consultaElDniYDevuelveNombresEnTitulo() {
        servidor.expect(requestTo(URL + "/dni/info/70123321")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-prueba"))
                .andRespond(withSuccess("""
                        {"status":200,"success":true,"message":"Exito","data":{"numero":"70123321","nombres":"ROSA MARIA",
                         "apellido_paterno":"HUAMAN","apellido_materno":"QUISPE","nombre_completo":"HUAMAN QUISPE, ROSA MARIA"}}
                        """, MediaType.APPLICATION_JSON));
        DniRespuesta r = proveedor.consultarDni("70123321").orElseThrow();
        assertThat(r.nombres()).isEqualTo("Rosa Maria");
        assertThat(r.apellidoPaterno()).isEqualTo("Huaman");
        assertThat(r.apellidos()).isEqualTo("Huaman Quispe");
        assertThat(r.fuente()).isEqualTo("RENIEC (Factiliza)");
        servidor.verify();
    }

    @Test
    void dniInexistenteDevuelveVacio() {
        servidor.expect(requestTo(URL + "/dni/info/11111111"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"success\":false,\"message\":\"No se encontraron resultados\"}"));
        assertThat(proveedor.consultarDni("11111111")).isEmpty();
    }

    @Test
    void respuestaSinExitoDevuelveVacio() {
        servidor.expect(requestTo(URL + "/dni/info/22222222"))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"sin datos\"}", MediaType.APPLICATION_JSON));
        assertThat(proveedor.consultarDni("22222222")).isEmpty();
    }

    @Test
    void errorDelProveedorEs503ConsultaNoDisponible() {
        servidor.expect(requestTo(URL + "/dni/info/70123321")).andRespond(withServerError());
        assertThatThrownBy(() -> proveedor.consultarDni("70123321"))
                .isInstanceOf(NegocioExcepcion.class)
                .satisfies(e -> {
                    assertThat(((NegocioExcepcion) e).getStatus()).isEqualTo(503);
                    assertThat(((NegocioExcepcion) e).getCodigo()).isEqualTo("CONSULTA_NO_DISPONIBLE");
                });
    }

    @Test
    void tokenInvalidoTambienEs503() {
        servidor.expect(requestTo(URL + "/ruc/info/20000000001")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> proveedor.consultarRuc("20000000001")).isInstanceOf(NegocioExcepcion.class);
    }

    @Test
    void consultaElRuc() {
        servidor.expect(requestTo(URL + "/ruc/info/20000000001"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"numero":"20000000001","nombre_o_razon_social":"LOS ALCANFORES SPORT E.I.R.L.",
                         "estado":"ACTIVO","condicion":"HABIDO","direccion_completa":"CAR. CENTRAL KM. 11.5 ATE - LIMA"}}
                        """, MediaType.APPLICATION_JSON));
        RucRespuesta r = proveedor.consultarRuc("20000000001").orElseThrow();
        assertThat(r.razonSocial()).isEqualTo("LOS ALCANFORES SPORT E.I.R.L.");
        assertThat(r.direccion()).contains("CENTRAL");
        assertThat(r.estado()).isEqualTo("ACTIVO");
        assertThat(r.condicion()).isEqualTo("HABIDO");
        assertThat(r.fuente()).isEqualTo("SUNAT (Factiliza)");
    }

    @Test
    void elDniInexistenteQueTardaMasDe5SegundosSigueSiendoNoEncontrado() throws Exception {
        // Factiliza real tarda ~11 s en responder 404 a un DNI inexistente; antes el cliente cortaba a los 5 s (503).
        HttpServer lento = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lento.createContext("/v1/dni/info/", intercambio -> {
            try {
                Thread.sleep(6_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{\"status\":404,\"success\":false,\"message\":\"No se encontraron resultados\"}"
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(404, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        lento.start();
        try {
            FactilizaProveedor real = new FactilizaProveedor(
                    RestClient.builder().requestFactory(ClientesHttpConfig.fabricaDocumentos()),
                    new ReservasPropiedades.Factiliza("token-prueba", "http://127.0.0.1:" + lento.getAddress().getPort() + "/v1"));
            long inicio = System.nanoTime();
            assertThat(real.consultarDni("99010005")).isEmpty();
            assertThat((System.nanoTime() - inicio) / 1_000_000).isGreaterThanOrEqualTo(5_500);
        } finally {
            lento.stop(0);
        }
    }

    @Test
    void errores400y422SonNoEncontrado() {
        servidor.expect(requestTo(URL + "/ruc/info/20000000001")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        servidor.expect(requestTo(URL + "/dni/info/99010005")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThat(proveedor.consultarRuc("20000000001")).isEmpty();
        assertThat(proveedor.consultarDni("99010005")).isEmpty();
        servidor.verify();
    }

    @Test
    void sinTokenNoEstaDisponible() {
        FactilizaProveedor sinToken = new FactilizaProveedor(RestClient.builder(), new ReservasPropiedades.Factiliza("", URL));
        assertThat(sinToken.disponible()).isFalse();
        assertThatThrownBy(() -> sinToken.consultarDni("70123321"))
                .isInstanceOf(NegocioExcepcion.class).hasMessageContaining("RENIEC");
    }
}
