package pe.alcanfores.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import pe.alcanfores.comun.config.AlcanforesPropiedades;

import java.util.Map;

/** Ruta de Canchita (asistente-service) y cupo del limitador por conversación con el token interno. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AsistenteGatewayTest {

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry r) {
        r.add("ASISTENTE_URL", () -> "http://localhost:" + ServicioSimulado.puerto());
        r.add("spring.cloud.discovery.client.simple.instances.reservas-service[0].uri",
                () -> "http://localhost:" + ServicioSimulado.puerto());
    }

    @Autowired
    private WebTestClient web;
    @Autowired
    private AlcanforesPropiedades propiedades;

    private WebTestClient.ResponseSpec mensaje(String ip) {
        return web.post().uri("/api/asistente/web/mensaje").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("sesion", "sesion-prueba-0001", "texto", "hola"))
                .exchange();
    }

    private WebTestClient.ResponseSpec reservar(String token, String conversacion) {
        return web.post().uri("/api/reservas").header("X-Forwarded-For", "172.18.0.9")
                .header("X-Interno-Token", token).header("X-Canchita-Conversacion", conversacion)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("idsTurno", new int[] {1}))
                .exchange();
    }

    @Test
    void enrutaAlAsistenteYNoLeReenviaElTokenInterno() {
        web.get().uri("/api/asistente/web/historial?sesion=sesion-prueba-0001")
                .header("X-Interno-Token", propiedades.getInternoToken())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-RateLimit-Limit")
                .expectBody()
                .jsonPath("$.ruta").isEqualTo("/api/asistente/web/historial")
                .jsonPath("$.internoToken").isEqualTo("");
    }

    @Test
    void losMensajesDelChatTienen20PorMinutoPorIp() {
        for (int i = 0; i < 20; i++) {
            mensaje("190.40.7.1").expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Limit", "20");
        }
        mensaje("190.40.7.1").expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.codigo").isEqualTo("DEMASIADAS_SOLICITUDES");
        mensaje("190.40.7.2").expectStatus().isOk();
    }

    @Test
    void elCuerpoDelChatNoPasaDe6Mb() {
        web.post().uri("/api/asistente/web/mensaje").header("X-Forwarded-For", "190.40.7.3")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(new byte[7 * 1024 * 1024])
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void cadaConversacionDeCanchitaTieneSuPropioCupoDeReservas() {
        String token = propiedades.getInternoToken();
        for (int i = 0; i < 10; i++) {
            reservar(token, "c101").expectStatus().isOk();
        }
        reservar(token, "c101").expectStatus().isEqualTo(429);
        // Otra conversación, misma IP del asistente: cupo propio.
        reservar(token, "c102").expectStatus().isOk().expectBody().jsonPath("$.internoToken").isEqualTo("");
    }

    @Test
    void sinTokenInternoValidoLaConversacionSeIgnoraYCuentaLaIp() {
        for (int i = 0; i < 10; i++) {
            web.post().uri("/api/reservas").header("X-Forwarded-For", "200.10.20.30")
                    .header("X-Interno-Token", "token-falso").header("X-Canchita-Conversacion", "c" + i)
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("idTurno", 1))
                    .exchange().expectStatus().isOk();
        }
        web.post().uri("/api/reservas").header("X-Forwarded-For", "200.10.20.30")
                .header("X-Interno-Token", "token-falso").header("X-Canchita-Conversacion", "c-otra")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("idTurno", 1))
                .exchange().expectStatus().isEqualTo(429);
    }
}
