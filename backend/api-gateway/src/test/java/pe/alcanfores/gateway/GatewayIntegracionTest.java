package pe.alcanfores.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegracionTest {

    private static final String FRENTE = "http://localhost:5186";

    @DynamicPropertySource
    static void instancias(DynamicPropertyRegistry r) {
        r.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
                () -> "http://localhost:" + ServicioSimulado.puerto());
    }

    /** Cliente HTTP real contra el puerto aleatorio (lo registra @SpringBootTest con RANDOM_PORT). */
    @Autowired
    private WebTestClient web;
    @Autowired
    private JwtServicio jwt;
    @Autowired
    private AlcanforesPropiedades propiedades;
    @LocalServerPort
    private int puerto;

    private final UsuarioToken rosa = new UsuarioToken(1L, "rhuaman", "Rosa Huamán", "ADMIN");

    // ---------------------------------------------------------------- enrutamiento y cabeceras

    @Test
    void enrutaAlServicioYPropagaElIdDeSolicitud() {
        web.get().uri("/api/auth/yo").header("X-Request-Id", "prueba-1234-abcd")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "prueba-1234-abcd")
                .expectBody()
                .jsonPath("$.ruta").isEqualTo("/api/auth/yo")
                .jsonPath("$.requestId").isEqualTo("prueba-1234-abcd");
    }

    @Test
    void generaElIdDeSolicitudSiNoLlegaOEsInvalido() {
        web.get().uri("/api/auth/yo").header("X-Request-Id", "malo con espacios")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Request-Id", id -> assertThat(id).hasSize(36).doesNotContain(" "));
    }

    @Test
    void quitaElTokenInternoQueMandeElCliente() {
        web.get().uri("/api/auth/yo").header("X-Interno-Token", propiedades.getInternoToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.internoToken").isEqualTo("");
    }

    @Test
    void laIpDelClienteVaPrimeroEnXForwardedFor() {
        web.get().uri("/api/auth/yo").header("X-Forwarded-For", "190.40.1.2, 10.0.0.9")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ip").value(ip -> assertThat(String.valueOf(ip)).startsWith("190.40.1.2,")
                        .doesNotContain("10.0.0.9"));
        web.get().uri("/api/auth/yo")
                .exchange()
                .expectBody().jsonPath("$.ip").value(ip -> assertThat(String.valueOf(ip)).startsWith("127.0.0.1"));
    }

    @Test
    void elLoginEsPublico() {
        web.post().uri("/api/auth/login").header("X-Forwarded-For", "10.0.0.50")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("usuario", "rhuaman", "contrasena", "x"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "10")
                .expectBody().jsonPath("$.metodo").isEqualTo("POST");
    }

    @Test
    void rutaDesconocidaResponde404ConErrorApi() {
        web.get().uri("/api/no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("NO_ENCONTRADO")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.ruta").isEqualTo("/api/no-existe");
    }

    @Test
    void metodoNoPermitidoResponde405ConErrorApi() {
        web.delete().uri("/api/estado")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectBody().jsonPath("$.codigo").isEqualTo("METODO_NO_PERMITIDO");
    }

    // ---------------------------------------------------------------- CORS

    @Test
    void preflightDelFrontPermitido() {
        web.options().uri("/api/reservas")
                .header(HttpHeaders.ORIGIN, FRENTE)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-request-id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRENTE)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, m -> assertThat(m).contains("POST", "PATCH", "DELETE"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        h -> assertThat(h.toLowerCase()).contains("authorization", "content-type", "x-request-id"));
    }

    @Test
    void aceptaVariosOrigenesSeparadosPorComa() {
        web.options().uri("/api/catalogo/canchas")
                .header(HttpHeaders.ORIGIN, "https://alcanfores.pe")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://alcanfores.pe");
    }

    @Test
    void preflightDeOtroOrigenRechazado() {
        web.options().uri("/api/reservas")
                .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void lasRespuestasExponenIdYContentDisposition() {
        web.get().uri("/api/auth/yo").header(HttpHeaders.ORIGIN, FRENTE)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRENTE)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        h -> assertThat(h).contains("X-Request-Id", "Content-Disposition"));
    }

    @Test
    void losErroresDelGatewayTambienLlevanCors() {
        web.get().uri("/api/admin/usuarios").header(HttpHeaders.ORIGIN, FRENTE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRENTE);
    }

    // ---------------------------------------------------------------- rutas internas

    @Test
    void bloqueaRutasInternas() {
        web.get().uri("/interno/reservas/ALC-ABCDEF")
                .header("X-Interno-Token", propiedades.getInternoToken())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("NO_ENCONTRADO")
                .jsonPath("$.mensaje").isEqualTo("La ruta solicitada no existe.");
    }

    @Test
    void bloqueaTrucosParaLlegarARutasInternas() {
        for (String truco : new String[]{"/api/auth/../../interno/x", "/api/auth/..;/..;/interno/x",
                "/%69nterno/reservas", "/api/auth/%2e%2e/%2e%2e/interno/x", "/INTERNO/reservas"}) {
            web.get().uri(URI.create("http://localhost:" + puerto + truco))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.codigo").isEqualTo("NO_ENCONTRADO");
        }
    }

    // ---------------------------------------------------------------- JWT en /api/admin/**

    @Test
    void adminSinTokenResponde401() {
        web.get().uri("/api/admin/usuarios")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO")
                .jsonPath("$.mensaje").isEqualTo("Inicia sesión para continuar.");
    }

    @Test
    void adminConTokenVencidoAvisaQueLaSesionVencio() {
        String vencido = new JwtServicio(propiedades.getJwt().getSecreto(), -1).generar(rosa);
        web.get().uri("/api/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.mensaje").isEqualTo("Tu sesión venció. Vuelve a iniciar sesión.");
    }

    @Test
    void adminConTokenFirmadoPorOtroResponde401() {
        String ajeno = new JwtServicio("otro-secreto-de-al-menos-32-bytes-para-firmar", 8).generar(rosa);
        web.get().uri("/api/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.mensaje").isEqualTo("El token de sesión no es válido.");
    }

    @Test
    void adminConTokenValidoLlegaAlServicio() {
        web.get().uri("/api/admin/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generar(rosa))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ruta").isEqualTo("/api/admin/usuarios");
    }

    // ---------------------------------------------------------------- límite de solicitudes

    @Test
    void elUndecimoLoginEnUnMinutoResponde429() {
        for (int i = 0; i < 10; i++) {
            web.post().uri("/api/auth/login").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange().expectStatus().isOk();
        }
        web.post().uri("/api/auth/login").header("X-Forwarded-For", "203.0.113.7")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("DEMASIADAS_SOLICITUDES")
                .jsonPath("$.mensaje").value(containsString("Espera"));

        // Otra IP no se ve afectada.
        web.post().uri("/api/auth/login").header("X-Forwarded-For", "203.0.113.8")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isOk();
    }

    @Test
    void elLimiteDeReservasSoloCuentaElPost() {
        for (int i = 0; i < 12; i++) {
            web.get().uri("/api/reservas/ALC-ABCDEF?dni=12345678").header("X-Forwarded-For", "198.51.100.4")
                    .exchange().expectHeader().doesNotExist("X-RateLimit-Limit");
        }
    }

    // ---------------------------------------------------------------- tamaño del cuerpo

    @Test
    void rechazaCuerposDeMasDeSeisMegas() {
        web.post().uri("/api/pagos/comprobante")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[7 * 1024 * 1024])
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.codigo").isEqualTo("CUERPO_MUY_GRANDE");
    }

    @Test
    void aceptaCuerposDentroDelLimite() {
        web.post().uri("/api/auth/subida").contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[1024 * 1024])
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.metodo").isEqualTo("POST");
    }

    // ---------------------------------------------------------------- circuit breaker

    @Test
    void servicioSinInstanciasRespondeElFallback() {
        web.get().uri("/api/catalogo/canchas")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE")
                .jsonPath("$.mensaje").isEqualTo("El servicio de catálogo no responde. Intenta en unos segundos.")
                .jsonPath("$.ruta").isEqualTo("/api/catalogo/canchas");
    }

    @Test
    void servicioLentoRespondeElFallbackSinEsperarDeMas() {
        long inicio = System.nanoTime();
        web.get().uri("/api/auth/lento")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("El servicio de acceso no responde. Intenta en unos segundos.");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void cadaServicioTieneSuNombreEnElFallback() {
        Map<String, String> rutas = Map.of(
                "/api/disponibilidad?fecha=2026-11-20", "reservas",
                "/api/pagos/metodos", "pagos",
                "/api/admin/notificaciones", "notificaciones");
        String token = "Bearer " + jwt.generar(rosa);
        rutas.forEach((ruta, nombre) -> web.method(HttpMethod.GET).uri(ruta).header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.mensaje").value(containsString("servicio de " + nombre + " ")));
    }

    // ---------------------------------------------------------------- estado del sistema

    @Test
    void estadoDelSistema() {
        web.get().uri("/api/estado")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.generadoEn").exists()
                .jsonPath("$.servicios[*].nombre").value(hasItems("discovery-server", "api-gateway", "auth-service",
                        "catalogo-service", "reservas-service", "pagos-service", "notificaciones-service"))
                .jsonPath("$.servicios[?(@.nombre=='discovery-server')].estado").isEqualTo("DOWN")
                .jsonPath("$.servicios[?(@.nombre=='api-gateway')].estado").isEqualTo("UP")
                .jsonPath("$.servicios[?(@.nombre=='auth-service')].estado").isEqualTo("UP")
                .jsonPath("$.servicios[?(@.nombre=='auth-service')].instancias").isEqualTo(1)
                .jsonPath("$.servicios[?(@.nombre=='catalogo-service')].estado").isEqualTo("DOWN")
                .jsonPath("$.servicios[?(@.nombre=='catalogo-service')].instancias").isEqualTo(0);
    }
}
