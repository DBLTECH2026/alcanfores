package pe.alcanfores.notificaciones.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.servicio.AvisoServicio;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControladoresAdminTest extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtServicio jwt;
    @Autowired
    private AvisoServicio avisos;

    private String admin;
    private String recepcion;

    @BeforeEach
    void tokens() {
        admin = "Bearer " + jwt.generar(new UsuarioToken(1L, "rhuaman", "Rosa Huamán", "ADMIN"));
        recepcion = "Bearer " + jwt.generar(new UsuarioToken(2L, "mtorres", "Marco Torres", "RECEPCION"));
        when(reservasCliente.constanciaPdf(anyString())).thenReturn(Datos.PDF);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder r, String token) {
        return r.header("Authorization", token);
    }

    private Notificacion fallida() {
        Notificacion n = new Notificacion(Datos.CODIGO, "reserva.confirmada", CanalNotificacion.WHATSAPP, "51987654321",
                "Reserva confirmada", "*Hola*", List.of(), Tiempo.ahora());
        n.registrarResultado(EstadoNotificacion.FALLIDO, "Evolution API respondió 500", Tiempo.ahora());
        return notificaciones.save(n);
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    @DisplayName("401 sin token y con token inválido (cuerpo de error estándar)")
    void sinToken() throws Exception {
        mvc.perform(get("/api/admin/notificaciones"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(conToken(get("/api/admin/whatsapp/estado"), "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token de sesión no es válido."));
    }

    @Test
    @DisplayName("RECEPCION ve la bandeja pero no reintenta, ni recordatorios, ni WhatsApp (403)")
    void recepcion() throws Exception {
        Notificacion n = fallida();
        mvc.perform(conToken(get("/api/admin/notificaciones"), recepcion)).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/admin/notificaciones/" + n.getId()), recepcion)).andExpect(status().isOk());
        mvc.perform(conToken(post("/api/admin/notificaciones/" + n.getId() + "/reintentar"), recepcion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(conToken(get("/api/admin/recordatorios"), recepcion)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/admin/recordatorios/ejecutar"), recepcion)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/admin/whatsapp/estado"), recepcion)).andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"987654321\",\"mensaje\":\"hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rutas fuera de /api/admin se niegan y la salud es pública")
    void otrasRutas() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(conToken(get("/api/otra-cosa"), admin)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- bandeja

    @Test
    @DisplayName("Bandeja paginada con filtros por canal, estado, evento y texto")
    void bandeja() throws Exception {
        avisos.alConfirmarReserva(Datos.confirmada());
        fallida();

        mvc.perform(conToken(get("/api/admin/notificaciones"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.contenido[0].eventoTexto").value("Reserva confirmada"));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("canal", "CORREO"), admin))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].destino").value("diego.ramos@example.com"))
                .andExpect(jsonPath("$.contenido[0].adjuntos").value(1));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("estado", "FALLIDO"), admin))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].ultimoError").value("Evolution API respondió 500"));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("evento", "reserva.confirmada").param("q", "ALC-7K3M9P")
                        .param("desde", Tiempo.hoy().toString()).param("hasta", Tiempo.hoy().toString())
                        .param("sort", "canal,asc").param("size", "2"), admin))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("q", "nadie@nada.pe"), admin))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("Filtros inválidos → 400 con el campo")
    void filtrosInvalidos() throws Exception {
        mvc.perform(conToken(get("/api/admin/notificaciones").param("canal", "FAX"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("canal"));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("size", "500"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El tamaño de página va de 1 a 100."));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("sort", "cuerpo,desc"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("sort"));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("desde", "2026-11-20").param("hasta", "2026-11-01"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        mvc.perform(conToken(get("/api/admin/notificaciones").param("q", "x".repeat(61)), admin))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(get("/api/admin/recordatorios").param("estado", "RARO"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Detalle: cuerpo HTML, vista previa con el logo incrustado y adjuntos; 404 si no existe")
    void detalle() throws Exception {
        avisos.alConfirmarReserva(Datos.confirmada());
        Notificacion correo = notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO).stream()
                .filter(n -> n.getCanal() == CanalNotificacion.CORREO).findFirst().orElseThrow();

        mvc.perform(conToken(get("/api/admin/notificaciones/" + correo.getId()), recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formato").value("HTML"))
                .andExpect(jsonPath("$.estado").value("SIMULADO"))
                .andExpect(jsonPath("$.cuerpo", containsString("cid:logo-alcanfores")))
                .andExpect(jsonPath("$.vistaPrevia", containsString("data:image/png;base64,")))
                .andExpect(jsonPath("$.adjuntos[0].tipo").value("CONSTANCIA"))
                .andExpect(jsonPath("$.adjuntos[0].estado").value("ADJUNTADO"))
                .andExpect(jsonPath("$.reintentable").value(false));
        mvc.perform(conToken(get("/api/admin/notificaciones/999999"), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(conToken(get("/api/admin/notificaciones/abc"), admin)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reintentar: solo FALLIDOS (422 si no), queda auditado")
    void reintentar() throws Exception {
        Notificacion n = fallida();

        mvc.perform(conToken(post("/api/admin/notificaciones/" + n.getId() + "/reintentar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIMULADO"))
                .andExpect(jsonPath("$.intentos").value(2))
                .andExpect(jsonPath("$.formato").value("TEXTO"));
        verify(auditor).registrar(eq("REINTENTAR_NOTIFICACION"), eq("notificacion"), eq(n.getId()), contains("SIMULADO"));

        mvc.perform(conToken(post("/api/admin/notificaciones/" + n.getId() + "/reintentar"), admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("NO_REINTENTABLE"));
    }

    // ---------------------------------------------------------------- recordatorios

    @Test
    @DisplayName("Recordatorios: lista por estado y ejecución manual")
    void recordatorios() throws Exception {
        Datos.reloj(Datos.INICIO.minusDays(1));
        avisos.alConfirmarReserva(Datos.confirmada());

        mvc.perform(conToken(get("/api/admin/recordatorios").param("estado", "PROGRAMADO"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].codigoReserva").value(Datos.CODIGO))
                .andExpect(jsonPath("$.contenido[0].enviarEn").value("2026-11-20T17:00:00"))
                .andExpect(jsonPath("$.contenido[0].inicioReserva").value("2026-11-20T19:00:00"));
        mvc.perform(conToken(post("/api/admin/recordatorios/ejecutar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisados").value(0));
        verify(auditor).registrar(eq("EJECUTAR_RECORDATORIOS"), eq("recordatorio"), eq(null), anyString());
    }

    // ---------------------------------------------------------------- WhatsApp

    @Test
    @DisplayName("WhatsApp sin configurar: estado NO_CONFIGURADO y conectar → 409")
    void whatsappSinConfigurar() throws Exception {
        mvc.perform(conToken(get("/api/admin/whatsapp/estado"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false))
                .andExpect(jsonPath("$.instancia").value("alcanfores"))
                .andExpect(jsonPath("$.estado").value("NO_CONFIGURADO"));
        mvc.perform(conToken(post("/api/admin/whatsapp/conectar"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("WHATSAPP_NO_CONFIGURADO"));
        mvc.perform(conToken(post("/api/admin/whatsapp/desconectar"), admin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Conectar con un teléfono inválido → 400")
    void conectarTelefonoInvalido() throws Exception {
        mvc.perform(conToken(post("/api/admin/whatsapp/conectar"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
    }

    @Test
    @DisplayName("Prueba de WhatsApp: valida teléfono y mensaje (1 a 1000)")
    void pruebaValidaciones() throws Exception {
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"887654321\",\"mensaje\":\"Hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa un celular peruano de 9 dígitos que empiece con 9."));
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"987654321\",\"mensaje\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("mensaje"));
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"987654321\",\"mensaje\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe el mensaje de prueba (1 a 1000 caracteres)."));
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)));
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auditor);
    }

    @Test
    @DisplayName("Prueba de WhatsApp válida: queda SIMULADA en la bandeja y auditada")
    void pruebaValida() throws Exception {
        mvc.perform(conToken(post("/api/admin/whatsapp/prueba"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+51 987 654 321\",\"mensaje\":\"Prueba desde el panel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento").value("whatsapp.prueba"))
                .andExpect(jsonPath("$.destino").value("51987654321"))
                .andExpect(jsonPath("$.estado").value("SIMULADO"))
                .andExpect(jsonPath("$.cuerpo").value("Prueba desde el panel"))
                .andExpect(jsonPath("$.eventoTexto", startsWith("Prueba")));
        verify(auditor).registrar(eq("PRUEBA_WHATSAPP"), eq("notificacion"), any(),
                contains("51987654321"));
    }
}
