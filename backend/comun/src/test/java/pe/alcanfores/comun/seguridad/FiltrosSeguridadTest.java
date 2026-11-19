package pe.alcanfores.comun.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.alcanfores.comun.error.NegocioExcepcion;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Filtros, interceptor y manejadores de seguridad con objetos Mock de Servlet (sin levantar Spring). */
class FiltrosSeguridadTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static JsonNode cuerpo(MockHttpServletResponse res) throws Exception {
        return JSON.readTree(res.getContentAsByteArray());
    }

    // =====================================================================================================
    @Nested
    class FiltroJwt {

        private final JwtServicio jwt = new JwtServicio(JwtServicioTest.SECRETO, 8);
        private final JwtFiltroAutenticacion filtro = new JwtFiltroAutenticacion(jwt);

        private MockHttpServletRequest solicitud(String authorization) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/reservas");
            if (authorization != null) {
                req.addHeader("Authorization", authorization);
            }
            return req;
        }

        @Test
        void tokenValidoDejaAlUsuarioConSuRol() throws Exception {
            UsuarioToken u = new UsuarioToken(7L, "rhuaman", "Rosa", "ADMIN");
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(solicitud("Bearer " + jwt.generar(u)), new MockHttpServletResponse(), cadena);

            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            assertThat(a).isNotNull();
            assertThat(a.getPrincipal()).isEqualTo(u);
            assertThat(a.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
            assertThat(cadena.getRequest()).as("la cadena continúa").isNotNull();
            assertThat(SeguridadUtil.requerido()).isEqualTo(u);
        }

        @Test
        void tokenConEspaciosExtraTambienSirve() throws Exception {
            UsuarioToken u = new UsuarioToken(8L, "jperez", "Juan", "RECEPCION");
            filtro.doFilter(solicitud("Bearer   " + jwt.generar(u) + "  "), new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SeguridadUtil.actual()).contains(u);
        }

        @Test
        void tokenVencidoMarcaLaSolicitudYNoAutentica() throws Exception {
            String vencido = new JwtServicio(JwtServicioTest.SECRETO, -1).generar(new UsuarioToken(7L, "a", "A", "ADMIN"));
            MockHttpServletRequest req = solicitud("Bearer " + vencido);
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(req, new MockHttpServletResponse(), cadena);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(req.getAttribute(JwtFiltroAutenticacion.ATRIBUTO_ERROR)).isEqualTo("Tu sesión venció. Vuelve a iniciar sesión.");
            assertThat(cadena.getRequest()).isNotNull();
        }

        @Test
        void tokenInvalidoOVacioMarcaLaSolicitud() throws Exception {
            for (String cabecera : List.of("Bearer basura", "Bearer ", "Bearer a.b.c")) {
                MockHttpServletRequest req = solicitud(cabecera);
                filtro.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
                assertThat(req.getAttribute(JwtFiltroAutenticacion.ATRIBUTO_ERROR)).as(cabecera)
                        .isEqualTo("El token de sesión no es válido.");
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            }
        }

        @Test
        void sinCabeceraOConOtroEsquemaNoHaceNada() throws Exception {
            for (String cabecera : new String[]{null, "Basic dXNlcjpwYXNz", "bearer xyz"}) {
                MockHttpServletRequest req = solicitud(cabecera);
                MockFilterChain cadena = new MockFilterChain();
                filtro.doFilter(req, new MockHttpServletResponse(), cadena);
                assertThat(req.getAttribute(JwtFiltroAutenticacion.ATRIBUTO_ERROR)).isNull();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                assertThat(cadena.getRequest()).isNotNull();
            }
        }

        @Test
        void siYaHayAutenticacionNoLaReemplaza() throws Exception {
            var previa = new UsernamePasswordAuthenticationToken("servicio-interno", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(previa);
            filtro.doFilter(solicitud("Bearer " + jwt.generar(new UsuarioToken(7L, "a", "A", "ADMIN"))),
                    new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previa);
            assertThat(SeguridadUtil.actual()).isEmpty();
        }
    }

    // =====================================================================================================
    @Nested
    class FiltroInterno {

        private final InternoFiltro filtro = new InternoFiltro("token-secreto", JSON);

        private MockHttpServletRequest solicitud(String uri, String token) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
            if (token != null) {
                req.addHeader(InternoFiltro.CABECERA, token);
            }
            return req;
        }

        @Test
        void rutasNoInternasPasanSinTocarse() throws Exception {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(solicitud("/api/catalogo/canchas", null), res, cadena);
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(cadena.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void tokenCorrectoDaRolInterno() throws Exception {
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(solicitud("/interno/reservas/1", "token-secreto"), new MockHttpServletResponse(), cadena);
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            assertThat(a.getPrincipal()).isEqualTo("servicio-interno");
            assertThat(a.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_INTERNO");
            assertThat(cadena.getRequest()).isNotNull();
        }

        @Test
        void tokenIncorrectoOAusenteDa403ConCuerpoEstandar() throws Exception {
            for (String token : new String[]{null, "", "token-secret", "token-secreto-x"}) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                MockFilterChain cadena = new MockFilterChain();
                filtro.doFilter(solicitud("/interno/reservas/1", token), res, cadena);

                assertThat(res.getStatus()).as("token %s", token).isEqualTo(403);
                assertThat(res.getContentType()).startsWith("application/json");
                JsonNode cuerpo = cuerpo(res);
                assertThat(cuerpo.get("status").asInt()).isEqualTo(403);
                assertThat(cuerpo.get("codigo").asText()).isEqualTo("SIN_PERMISO");
                assertThat(cuerpo.get("mensaje").asText()).isEqualTo("Ruta interna: solo para otros microservicios.");
                assertThat(cuerpo.get("ruta").asText()).isEqualTo("/interno/reservas/1");
                assertThat(cadena.getRequest()).as("no continúa la cadena").isNull();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            }
        }

        @Test
        void sinTokenConfiguradoNadieEntra() throws Exception {
            for (String configurado : new String[]{null, ""}) {
                InternoFiltro sinToken = new InternoFiltro(configurado, JSON);
                MockHttpServletResponse res = new MockHttpServletResponse();
                sinToken.doFilter(solicitud("/interno/x", ""), res, new MockFilterChain());
                assertThat(res.getStatus()).isEqualTo(403);
            }
        }
    }

    // =====================================================================================================
    @Nested
    class IdSolicitud {

        private final FiltroIdSolicitud filtro = new FiltroIdSolicitud();

        private String filtrar(String idRecibido, MockHttpServletResponse res) throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
            if (idRecibido != null) {
                req.addHeader(FiltroIdSolicitud.CABECERA, idRecibido);
            }
            AtomicReference<String> enMdc = new AtomicReference<>();
            FilterChain cadena = (rq, rs) -> enMdc.set(MDC.get(FiltroIdSolicitud.MDC_CLAVE));
            filtro.doFilter(req, res, cadena);
            return enMdc.get();
        }

        @Test
        void reusaElIdDelGatewayYLoDevuelve() throws Exception {
            MockHttpServletResponse res = new MockHttpServletResponse();
            String enMdc = filtrar("abc-123", res);
            assertThat(enMdc).isEqualTo("abc-123");
            assertThat(res.getHeader("X-Request-Id")).isEqualTo("abc-123");
            assertThat(MDC.get(FiltroIdSolicitud.MDC_CLAVE)).as("se limpia al terminar").isNull();
        }

        @Test
        void sinIdVacioODemasiadoLargoGeneraUnUuid() throws Exception {
            for (String recibido : new String[]{null, "  ", "x".repeat(65)}) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                String enMdc = filtrar(recibido, res);
                assertThat(enMdc).matches("[0-9a-f-]{36}");
                assertThat(res.getHeader(FiltroIdSolicitud.CABECERA)).isEqualTo(enMdc);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();
            assertThat(filtrar("x".repeat(64), res)).isEqualTo("x".repeat(64));
        }

        @Test
        void limpiaElMdcAunqueLaCadenaFalle() {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
            FilterChain revienta = (rq, rs) -> {
                throw new IllegalStateException("falla");
            };
            assertThatThrownBy(() -> filtro.doFilter(req, new MockHttpServletResponse(), revienta))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get(FiltroIdSolicitud.MDC_CLAVE)).isNull();
        }
    }

    // =====================================================================================================
    @Nested
    class Interceptor {

        private final InterceptorInterno interceptor = new InterceptorInterno("token-secreto");

        @Test
        void agregaTokenInternoYPropagaIdDeSolicitud() throws Exception {
            MDC.put(FiltroIdSolicitud.MDC_CLAVE, "req-42");
            MockClientHttpRequest req = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://reservas/interno/x"));
            byte[] cuerpo = "{}".getBytes();
            AtomicReference<byte[]> enviado = new AtomicReference<>();
            var respuesta = interceptor.intercept(req, cuerpo, (r, b) -> {
                enviado.set(b);
                return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            });
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enviado.get()).isSameAs(cuerpo);
            assertThat(req.getHeaders().getFirst("X-Interno-Token")).isEqualTo("token-secreto");
            assertThat(req.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-42");
        }

        @Test
        void sinIdEnMdcNoAgregaLaCabecera() throws Exception {
            MockClientHttpRequest req = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://pagos/interno/y"));
            req.getHeaders().set("X-Interno-Token", "valor-viejo");
            interceptor.intercept(req, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.NO_CONTENT));
            assertThat(req.getHeaders().get("X-Interno-Token")).containsExactly("token-secreto");
            assertThat(req.getHeaders().containsKey("X-Request-Id")).isFalse();
        }
    }

    // =====================================================================================================
    @Nested
    class Manejadores {

        private final ManejadoresSeguridad manejadores = new ManejadoresSeguridad(JSON);

        @Test
        void entradaSinSesionDa401ConMensajeGenerico() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/pagos");
            MockHttpServletResponse res = new MockHttpServletResponse();
            manejadores.commence(req, res, new InsufficientAuthenticationException("x"));
            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentType()).startsWith("application/json");
            assertThat(res.getCharacterEncoding()).isEqualTo("UTF-8");
            JsonNode c = cuerpo(res);
            assertThat(c.get("codigo").asText()).isEqualTo("NO_AUTENTICADO");
            assertThat(c.get("mensaje").asText()).isEqualTo("Inicia sesión para continuar.");
            assertThat(c.get("ruta").asText()).isEqualTo("/api/admin/pagos");
            assertThat(c.get("errores").isArray()).isTrue();
        }

        @Test
        void entradaConTokenVencidoExplicaElMotivo() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/pagos");
            req.setAttribute(JwtFiltroAutenticacion.ATRIBUTO_ERROR, "Tu sesión venció. Vuelve a iniciar sesión.");
            MockHttpServletResponse res = new MockHttpServletResponse();
            manejadores.commence(req, res, new InsufficientAuthenticationException("x"));
            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(cuerpo(res).get("mensaje").asText()).isEqualTo("Tu sesión venció. Vuelve a iniciar sesión.");
        }

        @Test
        void accesoDenegadoDa403() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/admin/usuarios/3");
            MockHttpServletResponse res = new MockHttpServletResponse();
            manejadores.handle(req, res, new AccessDeniedException("no"));
            assertThat(res.getStatus()).isEqualTo(403);
            JsonNode c = cuerpo(res);
            assertThat(c.get("status").asInt()).isEqualTo(403);
            assertThat(c.get("codigo").asText()).isEqualTo("SIN_PERMISO");
            assertThat(c.get("mensaje").asText()).isEqualTo("Tu rol no tiene permiso para esta acción.");
        }
    }

    // =====================================================================================================
    @Nested
    class Util {

        @Test
        void sinAutenticacionNoHayUsuarioYRequeridoDa401() {
            assertThat(SeguridadUtil.actual()).isEmpty();
            assertThatThrownBy(SeguridadUtil::requerido).isInstanceOfSatisfying(NegocioExcepcion.class, e -> {
                assertThat(e.getStatus()).isEqualTo(401);
                assertThat(e.getCodigo()).isEqualTo("NO_AUTENTICADO");
            });
        }

        @Test
        void principalDeOtroTipoNoCuentaComoUsuario() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("servicio-interno", null, List.of()));
            assertThat(SeguridadUtil.actual()).isEmpty();
        }

        @Test
        void conUsuarioTokenLoDevuelve() {
            UsuarioToken u = new UsuarioToken(3L, "admin", "Admin", "ADMIN");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
            assertThat(SeguridadUtil.actual()).contains(u);
            assertThat(SeguridadUtil.requerido()).isSameAs(u);
        }

        @Test
        void rutasTecnicasAbiertas() {
            assertThat(SeguridadComun.RUTAS_TECNICAS).contains("/actuator/health", "/v3/api-docs/**", "/error")
                    .doesNotContain("/actuator/**", "/interno/**");
        }
    }
}
