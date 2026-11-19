package pe.alcanfores.auth.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.cliente.NotificacionesCliente;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.CodigoRecuperacion;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperar la contraseña con un código: opciones enmascaradas, envío (con el cliente interno de notificaciones
 * simulado), límites de reenvío, intentos, vencimiento, permiso de un solo uso, cliente y administrador.
 */
class RecuperacionIntegracionTest extends PruebaIntegracion {

    private static final String CLAVE_CLIENTE = "Futbol2026";
    private static final String NUEVA = "Nueva2027x";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 19, 10, 0);

    @MockitoBean
    private NotificacionesCliente notificaciones;

    private CuentaCliente lucia;

    @BeforeEach
    void preparar() {
        reloj(T0);
        lucia = crearCuenta("70123456", "lucia.mendoza@correo.pe", CLAVE_CLIENTE);
        when(notificaciones.enviar(any())).thenReturn(new NotificacionesCliente.Resultado("SIMULADO", true, null));
    }

    private static void reloj(LocalDateTime momento) {
        Tiempo.fijar(Clock.fixed(momento.atZone(Tiempo.ZONA).toInstant(), Tiempo.ZONA));
    }

    private ResultActions llamar(String paso, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(conJson(post("/api/auth/recuperar/" + paso), cuerpo));
    }

    private static Map<String, Object> cuerpo(String tipo, String usuario, Object... extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipoCuenta", tipo);
        m.put("usuario", usuario);
        for (int i = 0; i < extra.length; i += 2) {
            m.put((String) extra[i], extra[i + 1]);
        }
        return m;
    }

    /** Envía un código y devuelve el que recibió notificaciones-service (el real, que no se guarda). */
    private String enviarYCapturar(String tipo, String usuario, String canal) throws Exception {
        llamar("enviar", cuerpo(tipo, usuario, "canal", canal)).andExpect(status().isOk());
        ArgumentCaptor<NotificacionesCliente.Solicitud> c = ArgumentCaptor.forClass(NotificacionesCliente.Solicitud.class);
        verify(notificaciones, atLeastOnce()).enviar(c.capture());
        return c.getValue().codigo();
    }

    private String verificar(String tipo, String usuario, String codigo) throws Exception {
        String r = llamar("verificar", cuerpo(tipo, usuario, "codigo", codigo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("tokenRestablecer").asText();
    }

    private ResultActions restablecer(String token, String nueva, String confirmacion) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("tokenRestablecer", token);
        m.put("nueva", nueva);
        m.put("confirmacion", confirmacion);
        return llamar("restablecer", m);
    }

    @Test
    void opcionesConDestinosEnmascarados() throws Exception {
        llamar("opciones", cuerpo("CLIENTE", " 70123456 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canales[0].canal").value("CORREO"))
                .andExpect(jsonPath("$.canales[0].destino").value("l****@correo.pe"))
                .andExpect(jsonPath("$.canales[1].canal").value("WHATSAPP"))
                .andExpect(jsonPath("$.canales[1].destino").value("*** *** 321"));
        crearUsuario("rhuaman", Rol.ADMIN);
        llamar("opciones", cuerpo("ADMIN", "RHUAMAN@alcanfores.pe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canales.length()").value(1))
                .andExpect(jsonPath("$.canales[0].destino").value("r****@alcanfores.pe"));
    }

    @Test
    void cuentaInexistenteOInactivaResponde404ConElMismoMensaje() throws Exception {
        llamar("opciones", cuerpo("CLIENTE", "99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.mensaje").value("No encontramos una cuenta con esos datos."));
        lucia.setEstado(EstadoUsuario.INACTIVO);
        cuentas.save(lucia);
        llamar("enviar", cuerpo("CLIENTE", "70123456", "canal", "CORREO"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("CUENTA_NO_ENCONTRADA"));
        // Un cliente no es un usuario del panel (y viceversa)
        llamar("opciones", cuerpo("ADMIN", "lucia.mendoza@correo.pe")).andExpect(status().isNotFound());
    }

    @Test
    void validaciones() throws Exception {
        llamar("enviar", cuerpo(null, " ", "canal", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("tipoCuenta", "usuario", "canal")));
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", "12a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El código tiene 6 dígitos."));
        restablecer("x", "corta", "otra")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("nueva", "confirmacion")));
    }

    @Test
    void enviarGuardaSoloElHashYAvisaQueElCanalEstaSimulado() throws Exception {
        llamar("enviar", cuerpo("CLIENTE", "lucia.mendoza@correo.pe", "canal", "CORREO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("CORREO"))
                .andExpect(jsonPath("$.destino").value("l****@correo.pe"))
                .andExpect(jsonPath("$.expiraEn").value("2026-11-19T10:10:00"))
                .andExpect(jsonPath("$.reenviarEn").value("2026-11-19T10:01:00"))
                .andExpect(jsonPath("$.reenviarEnSegundos").value(60))
                .andExpect(jsonPath("$.simulado").value(true))
                .andExpect(jsonPath("$.codigoPrueba").doesNotExist());

        ArgumentCaptor<NotificacionesCliente.Solicitud> c = ArgumentCaptor.forClass(NotificacionesCliente.Solicitud.class);
        verify(notificaciones).enviar(c.capture());
        NotificacionesCliente.Solicitud s = c.getValue();
        assertThat(s.codigo()).matches("\\d{6}");
        assertThat(s.destino()).isEqualTo("lucia.mendoza@correo.pe");
        assertThat(s.motivo()).isEqualTo("RECUPERAR_CONTRASENA");
        assertThat(s.nombre()).isEqualTo("Lucía Mendoza Paredes");
        CodigoRecuperacion guardado = codigosRecuperacion.findAll().get(0);
        assertThat(guardado.getCodigoHash()).hasSize(64).doesNotContain(s.codigo());
        assertThat(guardado.getDestino()).isEqualTo("l****@correo.pe");
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).containsExactly("ENVIAR_CODIGO_RECUPERACION");
    }

    @Test
    void whatsappSoloSiHayCelular() throws Exception {
        assertThat(enviarYCapturar("CLIENTE", "70123456", "WHATSAPP")).matches("\\d{6}");
        ArgumentCaptor<NotificacionesCliente.Solicitud> c = ArgumentCaptor.forClass(NotificacionesCliente.Solicitud.class);
        verify(notificaciones).enviar(c.capture());
        assertThat(c.getValue().destino()).isEqualTo("987654321");

        crearUsuario("mtorres", Rol.RECEPCION);
        llamar("enviar", cuerpo("ADMIN", "mtorres", "canal", "WHATSAPP"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CANAL_NO_DISPONIBLE"));
    }

    @Test
    void reenviarTras60SegundosYMaximo3EnviosCada15Minutos() throws Exception {
        Map<String, Object> c = cuerpo("CLIENTE", "70123456", "canal", "CORREO");
        llamar("enviar", c).andExpect(status().isOk());
        reloj(T0.plusSeconds(30));
        llamar("enviar", c).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_ENVIOS"))
                .andExpect(jsonPath("$.mensaje").value("Espera 30 segundos para pedir otro código."));
        reloj(T0.plusSeconds(61));
        llamar("enviar", c).andExpect(status().isOk());
        reloj(T0.plusSeconds(122));
        llamar("enviar", c).andExpect(status().isOk());
        reloj(T0.plusMinutes(5));
        llamar("enviar", c).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value("Ya te enviamos 3 códigos en los últimos 15 minutos. Intenta de nuevo en 10 minutos."));
        reloj(T0.plusMinutes(15).plusSeconds(1));
        llamar("enviar", c).andExpect(status().isOk());
        verify(notificaciones, times(4)).enviar(any());
    }

    @Test
    void codigoIncorrectoCuentaIntentosYAlQuintoSeAnula() throws Exception {
        String codigo = enviarYCapturar("CLIENTE", "70123456", "CORREO");
        String malo = codigo.equals("000000") ? "111111" : "000000";
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", malo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INCORRECTO"))
                .andExpect(jsonPath("$.mensaje").value("El código no es correcto. Te quedan 4 intentos."))
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"));
        for (int i = 0; i < 3; i++) {
            llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", malo)).andExpect(jsonPath("$.codigo").value("CODIGO_INCORRECTO"));
        }
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", malo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
        // Ya anulado: ni el código correcto sirve
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", codigo))
                .andExpect(jsonPath("$.codigo").value("CODIGO_VENCIDO"));
    }

    @Test
    void codigoVencidoALos10MinutosYSoloValeElUltimo() throws Exception {
        String primero = enviarYCapturar("CLIENTE", "70123456", "CORREO");
        reloj(T0.plusMinutes(2));
        String segundo = enviarYCapturar("CLIENTE", "70123456", "CORREO");
        if (!primero.equals(segundo)) {
            llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", primero))
                    .andExpect(jsonPath("$.codigo").value("CODIGO_INCORRECTO"));
        }
        reloj(T0.plusMinutes(12).plusSeconds(1));
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", segundo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_VENCIDO"))
                .andExpect(jsonPath("$.mensaje").value("El código venció o ya no es válido. Pide uno nuevo."));
    }

    @Test
    void flujoCompletoDelClienteDesbloqueaYElPermisoEsDeUnSoloUso() throws Exception {
        lucia.setBloqueadoHasta(T0.plusMinutes(15));
        lucia.setIntentosFallidos(3);
        cuentas.save(lucia);
        String codigo = enviarYCapturar("CLIENTE", "70123456", "CORREO");
        String r = llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEn").value("2026-11-19T10:10:00"))
                .andReturn().getResponse().getContentAsString();
        JsonNode v = json.readTree(r);
        String token = v.get("tokenRestablecer").asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(codigosRecuperacion.findAll().get(0).getTokenHash()).isNotEqualTo(token).hasSize(64);

        restablecer(token, NUEVA, NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", containsString("Tu contraseña se cambió")));

        CuentaCliente despues = cuentas.findById(lucia.getId()).orElseThrow();
        assertThat(codificador.matches(NUEVA, despues.getContrasenaHash())).isTrue();
        assertThat(despues.getBloqueadoHasta()).isNull();
        assertThat(despues.getIntentosFallidos()).isZero();
        mvc.perform(conJson(post("/api/auth/clientes/login"), Map.of("usuario", "70123456", "contrasena", NUEVA)))
                .andExpect(status().isOk());
        mvc.perform(conJson(post("/api/auth/clientes/login"), Map.of("usuario", "70123456", "contrasena", CLAVE_CLIENTE)))
                .andExpect(status().isUnauthorized());
        restablecer(token, "Otra2028x", "Otra2028x")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
        llamar("verificar", cuerpo("CLIENTE", "70123456", "codigo", codigo)).andExpect(jsonPath("$.codigo").value("CODIGO_VENCIDO"));

        ArgumentCaptor<NotificacionesCliente.Solicitud> c = ArgumentCaptor.forClass(NotificacionesCliente.Solicitud.class);
        verify(notificaciones, times(2)).enviar(c.capture());
        assertThat(c.getAllValues().get(1)).satisfies(s -> {
            assertThat(s.motivo()).isEqualTo("CONTRASENA_CAMBIADA");
            assertThat(s.codigo()).isNull();
            assertThat(s.destino()).isEqualTo("lucia.mendoza@correo.pe");
        });
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).contains("RECUPERAR_CONTRASENA");
        assertThat(codigosRecuperacion.findAll().get(0).getEstado()).isEqualTo(CodigoRecuperacion.Estado.USADO);
    }

    @Test
    void administradorPorWhatsappYPermisoVencido() throws Exception {
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);
        rosa.setTelefono("987654254");
        administradores.save(rosa);
        llamar("opciones", cuerpo("ADMIN", "rhuaman")).andExpect(jsonPath("$.canales[1].destino").value("*** *** 254"));
        String codigo = enviarYCapturar("ADMIN", "rhuaman", "WHATSAPP");
        String token = verificar("ADMIN", "rhuaman", codigo);

        reloj(T0.plusMinutes(11));
        restablecer(token, NUEVA, NUEVA).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));

        reloj(T0.plusMinutes(12));
        String otro = enviarYCapturar("ADMIN", "rhuaman", "WHATSAPP");
        restablecer(verificar("ADMIN", "rhuaman", otro), NUEVA, NUEVA).andExpect(status().isOk());
        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", "rhuaman", "contrasena", NUEVA)))
                .andExpect(status().isOk());
        ArgumentCaptor<NotificacionesCliente.Solicitud> c = ArgumentCaptor.forClass(NotificacionesCliente.Solicitud.class);
        verify(notificaciones, times(3)).enviar(c.capture());
        assertThat(c.getAllValues().get(2).canal()).isEqualTo(CodigoRecuperacion.Canal.WHATSAPP);
        assertThat(c.getAllValues().get(2).destino()).isEqualTo("987654254");
    }

    @Test
    void siNotificacionesFallaOEstaCaidoResponde503() throws Exception {
        when(notificaciones.enviar(any())).thenReturn(new NotificacionesCliente.Resultado("FALLIDO", false, "SMTP caído"));
        llamar("enviar", cuerpo("CLIENTE", "70123456", "canal", "WHATSAPP"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("ENVIO_FALLIDO"))
                .andExpect(jsonPath("$.mensaje", containsString("por WhatsApp")));
        reloj(T0.plusMinutes(1));
        when(notificaciones.enviar(any())).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de notificaciones no responde. Intenta en unos segundos."));
        llamar("enviar", cuerpo("CLIENTE", "70123456", "canal", "CORREO"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    void siElAvisoDeCambioFallaLaContrasenaIgualSeCambia() throws Exception {
        String codigo = enviarYCapturar("CLIENTE", "70123456", "CORREO");
        String token = verificar("CLIENTE", "70123456", codigo);
        when(notificaciones.enviar(any())).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "caído"));

        restablecer(token, NUEVA, NUEVA).andExpect(status().isOk());

        assertThat(codificador.matches(NUEVA, cuentas.findById(lucia.getId()).orElseThrow().getContrasenaHash())).isTrue();
    }
}
