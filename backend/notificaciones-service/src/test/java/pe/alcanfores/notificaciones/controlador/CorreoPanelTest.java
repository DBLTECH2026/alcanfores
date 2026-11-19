package pe.alcanfores.notificaciones.controlador;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.canal.ProveedorCorreo;
import pe.alcanfores.notificaciones.config.CifradorClaves;
import pe.alcanfores.notificaciones.modelo.ConfigCorreo;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.repositorio.ConfigCorreoRepositorio;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correo configurable desde el panel contra un SMTP real con SSL (GreenMail SMTPS, puerto 3465, certificado de prueba):
 * guardado cifrado que nunca se devuelve, correo de prueba, errores explicados y envío interno de códigos enmascarados.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "alcanfores.notificaciones.correo.ssl-confiar=*")
class CorreoPanelTest extends PruebaIntegracion {

    private static final String BUZON = "reservas@alcanfores.pe";
    private static final String CLAVE_SMTP = "ClaveSmtp2026";

    @RegisterExtension
    static final GreenMailExtension SMTPS = new GreenMailExtension(ServerSetupTest.SMTPS)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(BUZON, BUZON, CLAVE_SMTP));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtServicio jwt;
    @Autowired
    private ConfigCorreoRepositorio configuraciones;
    @Autowired
    private ProveedorCorreo proveedor;
    @Autowired
    private CifradorClaves cifrador;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper json;

    private String admin;

    @BeforeEach
    void preparar() {
        configuraciones.deleteAll();
        proveedor.invalidar();
        admin = "Bearer " + jwt.generar(new UsuarioToken(1L, "rhuaman", "Rosa Huamán", "ADMIN"));
    }

    @AfterEach
    void limpiar() {
        configuraciones.deleteAll();
        proveedor.invalidar();
    }

    private Map<String, Object> config(int puerto, String clave) {
        Map<String, Object> m = new HashMap<>();
        m.put("host", "127.0.0.1");
        m.put("puerto", puerto);
        m.put("usuario", BUZON);
        m.put("clave", clave);
        m.put("remitente", BUZON);
        m.put("nombreRemitente", "");
        m.put("seguridad", "SSL");
        return m;
    }

    /** HTML del correo (recorre las partes multipart). */
    private static String html(Part parte) throws Exception {
        if (parte.getContent() instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                String h = html(mp.getBodyPart(i));
                if (h != null) {
                    return h;
                }
            }
            return null;
        }
        return parte.isMimeType("text/html") ? (String) parte.getContent() : null;
    }

    private ResultActions guardar(Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put("/api/admin/correo").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions probar(String destino) throws Exception {
        return mvc.perform(post("/api/admin/correo/prueba").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"destino\":\"" + destino + "\"}"));
    }

    @Test
    @DisplayName("Sin configuración: origen NINGUNO; la prueba responde 422 CORREO_NO_ENVIADO")
    void sinConfiguracion() throws Exception {
        mvc.perform(get("/api/admin/correo").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false))
                .andExpect(jsonPath("$.origen").value("NINGUNO"))
                .andExpect(jsonPath("$.nombreRemitente").value("Complejo Deportivo Los Alcanfores"));
        probar("dueno@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_ENVIADO"))
                .andExpect(jsonPath("$.mensaje", containsString("guarda los datos del servidor")));
    }

    @Test
    @DisplayName("Validación por campo; la primera vez la contraseña es obligatoria")
    void validacion() throws Exception {
        Map<String, Object> malo = new HashMap<>();
        malo.put("host", "http://smtp.hostinger.com");
        malo.put("puerto", 70000);
        malo.put("usuario", "reservas");
        malo.put("remitente", "x");
        malo.put("nombreRemitente", "<script>");
        guardar(malo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("host", "puerto", "usuario", "remitente",
                        "nombreRemitente", "seguridad")));
        guardar(config(3465, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("clave"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa la contraseña del correo."));
        mvc.perform(get("/api/admin/correo").header("Authorization",
                        "Bearer " + jwt.generar(new UsuarioToken(2L, "mtorres", "Marco Torres", "RECEPCION"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guardar: la clave se guarda cifrada, nunca se devuelve y no va en la bitácora; vacía = se conserva")
    void guardaCifradoYNoDevuelveLaClave() throws Exception {
        guardar(config(3465, CLAVE_SMTP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("PANEL"))
                .andExpect(jsonPath("$.host").value("127.0.0.1"))
                .andExpect(jsonPath("$.puerto").value(3465))
                .andExpect(jsonPath("$.seguridad").value("SSL"))
                .andExpect(jsonPath("$.tieneClave").value(true))
                .andExpect(jsonPath("$.nombreRemitente").value("Complejo Deportivo Los Alcanfores"))
                .andExpect(jsonPath("$.actualizadoPor").value("rhuaman"))
                .andExpect(jsonPath("$.clave").doesNotExist())
                .andExpect(jsonPath("$.claveCifrada").doesNotExist());

        ConfigCorreo c = configuraciones.findById(ConfigCorreo.ID_UNICO).orElseThrow();
        assertThat(c.getClaveCifrada()).doesNotContain(CLAVE_SMTP);
        assertThat(cifrador.descifrar(c.getClaveCifrada())).contains(CLAVE_SMTP);
        verify(auditor).registrar(eq("CONFIGURAR_CORREO"), eq("config_correo"), eq((byte) 1),
                argThat(d -> d.contains("contraseña actualizada") && !d.contains(CLAVE_SMTP)));

        String antes = c.getClaveCifrada();
        guardar(config(3465, "")).andExpect(status().isOk()).andExpect(jsonPath("$.tieneClave").value(true));
        assertThat(configuraciones.findById(ConfigCorreo.ID_UNICO).orElseThrow().getClaveCifrada()).isEqualTo(antes);
        mvc.perform(get("/api/admin/correo").header("Authorization", admin))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain(CLAVE_SMTP));
    }

    @Test
    @DisplayName("Correo de prueba por SSL con la plantilla de la marca: 200 enviado")
    void pruebaEnviada() throws Exception {
        guardar(config(3465, CLAVE_SMTP)).andExpect(status().isOk());

        probar("Dueno@Example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(true))
                .andExpect(jsonPath("$.mensaje", containsString("dueno@example.com")));

        MimeMessage[] recibidos = SMTPS.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        assertThat(recibidos[0].getSubject()).isEqualTo("Prueba de correo · Complejo Deportivo Los Alcanfores");
        assertThat(recibidos[0].getFrom()[0].toString()).contains(BUZON);
        assertThat(html(recibidos[0])).contains("El correo funciona", "127.0.0.1:3465");
    }

    @Test
    @DisplayName("Errores explicados: contraseña incorrecta y puerto equivocado → 422 CORREO_NO_ENVIADO")
    void erroresExplicados() throws Exception {
        guardar(config(3465, "ClaveIncorrecta1")).andExpect(status().isOk());
        probar("dueno@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CORREO_NO_ENVIADO"))
                .andExpect(jsonPath("$.mensaje", containsString("rechazó el usuario o la contraseña")));

        guardar(config(3466, "")).andExpect(status().isOk());
        probar("dueno@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje", containsString("No pudimos conectarnos a 127.0.0.1 por el puerto 3466")));
    }

    @Test
    @DisplayName("Código por correo: sale con el código real y en la bandeja queda «******»")
    void codigoEnmascarado() throws Exception {
        guardar(config(3465, CLAVE_SMTP)).andExpect(status().isOk());

        mvc.perform(post("/interno/codigos").header("X-Interno-Token", "token-interno-solo-para-desarrollo-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"CORREO\",\"destino\":\"Lucia@Correo.pe\",\"nombre\":\"lucía\","
                                + "\"codigo\":\"482913\",\"motivo\":\"RECUPERAR_CONTRASENA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.simulado").value(false));

        MimeMessage[] recibidos = SMTPS.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        assertThat(recibidos[0].getAllRecipients()[0].toString()).isEqualTo("lucia@correo.pe");
        assertThat(html(recibidos[0])).contains("482913").doesNotContain("******");
        Notificacion guardada = notificaciones.findAll().get(0);
        assertThat(guardada.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(guardada.getEvento()).isEqualTo("cuenta.codigo");
        assertThat(guardada.getCuerpo()).contains("******", "Hola, Lucía").doesNotContain("482913");
    }
}
