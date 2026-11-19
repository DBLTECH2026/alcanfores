package pe.alcanfores.auth.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cuentas de clientes del portal: registro, login por DNI o correo, bloqueo, mi cuenta y permisos cruzados. */
class CuentaClienteIntegracionTest extends PruebaIntegracion {

    private static final String CLAVE_CLIENTE = "Futbol2026";

    private Map<String, Object> registro() {
        Map<String, Object> m = new HashMap<>();
        m.put("dni", "70123456");
        m.put("nombres", "  lucía ");
        m.put("apellidos", "mendoza  PAREDES");
        m.put("correo", "Lucia.Mendoza@Correo.PE");
        m.put("telefono", "+51 987 654 321");
        m.put("contrasena", CLAVE_CLIENTE);
        m.put("confirmacion", CLAVE_CLIENTE);
        m.put("aceptaTerminos", true);
        m.put("aceptaPrivacidad", true);
        return m;
    }

    private ResultActions registrar(Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(conJson(post("/api/auth/clientes/registro"), cuerpo));
    }

    private ResultActions login(String usuario, String clave) throws Exception {
        return mvc.perform(conJson(post("/api/auth/clientes/login"), Map.of("usuario", usuario, "contrasena", clave)));
    }

    @Test
    void registroCreaLaCuentaYDevuelveSesionConRolClienteYDni() throws Exception {
        String cuerpo = registrar(registro())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.cliente.dni").value("70123456"))
                .andExpect(jsonPath("$.cliente.nombres").value("Lucía"))
                .andExpect(jsonPath("$.cliente.apellidos").value("Mendoza Paredes"))
                .andExpect(jsonPath("$.cliente.correo").value("lucia.mendoza@correo.pe"))
                .andExpect(jsonPath("$.cliente.telefono").value("987654321"))
                .andExpect(jsonPath("$.cliente.contrasenaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode r = json.readTree(cuerpo);
        UsuarioToken t = jwt.validar(r.get("token").asText());
        assertThat(t.rol()).isEqualTo(UsuarioToken.CLIENTE);
        assertThat(t.dni()).isEqualTo("70123456");
        assertThat(t.usuario()).isEqualTo("lucia.mendoza@correo.pe");
        assertThat(t.nombre()).isEqualTo("Lucía Mendoza Paredes");
        assertThat(t.id()).isEqualTo(r.get("cliente").get("id").asLong());
        CuentaCliente guardada = cuentas.findById(t.id()).orElseThrow();
        assertThat(codificador.matches(CLAVE_CLIENTE, guardada.getContrasenaHash())).isTrue();
        assertThat(guardada.getUltimoAcceso()).isNotNull();
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).containsExactly("REGISTRAR_CUENTA");
        assertThat(bitacoras.findAll().get(0).getRol()).isEqualTo("CLIENTE");
    }

    @Test
    void dniOCorreoRepetidoResponde409ConElCampo() throws Exception {
        crearCuenta("70123456", "otra@correo.pe", CLAVE_CLIENTE);
        registrar(registro())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DNI_REGISTRADO"))
                .andExpect(jsonPath("$.errores[0].campo").value("dni"));

        Map<String, Object> otroDni = registro();
        otroDni.put("dni", "70999999");
        otroDni.put("correo", "OTRA@correo.pe");
        registrar(otroDni)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_REGISTRADO"))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"));
    }

    @Test
    void registroInvalidoMarcaCadaCampo() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("dni", "1234");
        m.put("nombres", "L4");
        m.put("apellidos", null);
        m.put("correo", "sin-arroba");
        m.put("telefono", "812345678");
        m.put("contrasena", "corta");
        m.put("confirmacion", "otra");
        m.put("aceptaTerminos", false);
        m.put("aceptaPrivacidad", null);
        registrar(m)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItems("dni", "nombres", "apellidos", "correo",
                        "telefono", "contrasena", "confirmacion", "aceptaTerminos", "aceptaPrivacidad")))
                .andExpect(jsonPath("$.errores[*].mensaje").value(hasItems("El DNI debe tener 8 dígitos.",
                        "Ingresa un celular peruano de 9 dígitos que empiece con 9.",
                        "Ingresa un correo válido; ahí te enviamos la copia de tu reserva.",
                        "Debes aceptar los términos y la política de privacidad.")));
        assertThat(cuentas.count()).isZero();
    }

    @Test
    void confirmacionDistintaSeMarcaEnConfirmacion() throws Exception {
        Map<String, Object> m = registro();
        m.put("confirmacion", "Futbol2027");
        registrar(m)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("confirmacion"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Las contraseñas no coinciden."));
    }

    @Test
    void loginPorDniYPorCorreo() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        String porDni = login(" 70123456 ", CLAVE_CLIENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.id").value(c.getId()))
                .andExpect(jsonPath("$.cliente.dni").value("70123456"))
                .andReturn().getResponse().getContentAsString();
        assertThat(jwt.validar(json.readTree(porDni).get("token").asText()).dni()).isEqualTo("70123456");

        login("LUCIA@Correo.pe", CLAVE_CLIENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.correo").value("lucia@correo.pe"));
        assertThat(cuentas.findById(c.getId()).orElseThrow().getUltimoAcceso()).isNotNull();
    }

    @Test
    void claveIncorrectaOCuentaInexistenteResponden401Igual() throws Exception {
        crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        login("70123456", "Otra2026x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("DNI, correo o contraseña incorrectos."));
        login("nadie@correo.pe", "Otra2026x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("DNI, correo o contraseña incorrectos."));
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo").value(hasItems("usuario", "contrasena")));
    }

    @Test
    void cincoIntentosFallidosBloqueanLaCuenta15Minutos() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        for (int i = 0; i < 4; i++) {
            login("70123456", "Mala2026x").andExpect(status().isUnauthorized());
        }
        login("70123456", "Mala2026x")
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.codigo").value("USUARIO_BLOQUEADO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("15 minutos")));
        // Bloqueada: ni la clave correcta entra.
        login("lucia@correo.pe", CLAVE_CLIENTE).andExpect(status().is(423));
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).contains("LOGIN_CLIENTE_BLOQUEADO");

        // Pasados los 15 minutos vuelve a entrar.
        Tiempo.fijar(Clock.offset(Clock.system(Tiempo.ZONA), Duration.ofMinutes(16)));
        login("70123456", CLAVE_CLIENTE).andExpect(status().isOk());
        CuentaCliente despues = cuentas.findById(c.getId()).orElseThrow();
        assertThat(despues.getIntentosFallidos()).isZero();
        assertThat(despues.getBloqueadoHasta()).isNull();
    }

    @Test
    void cuentaInexistenteTambienSeBloqueaParaNoDelatarla() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("70000001", "Mala2026x").andExpect(status().isUnauthorized());
        }
        login("70000001", "Mala2026x").andExpect(status().is(423));
        login("70000001", "Mala2026x").andExpect(status().is(423));
    }

    @Test
    void cuentaDesactivadaNoEntraYSuTokenDejaDeServir() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        String token = bearer(c);
        c.setEstado(EstadoUsuario.INACTIVO);
        cuentas.save(c);
        login("70123456", CLAVE_CLIENTE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
        mvc.perform(get("/api/auth/clientes/yo").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void yoYEditarMisDatos() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        crearCuenta("70999999", "ocupado@correo.pe", CLAVE_CLIENTE);
        mvc.perform(get("/api/auth/clientes/yo").header("Authorization", bearer(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("70123456"))
                .andExpect(jsonPath("$.nombres").value("Lucía"));

        Map<String, Object> datos = new HashMap<>(Map.of("nombres", "lucía andrea", "apellidos", "mendoza paredes",
                "correo", "Nuevo@Correo.pe", "telefono", "912 345 678"));
        mvc.perform(conJson(put("/api/auth/clientes/yo").header("Authorization", bearer(c)), datos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Lucía Andrea"))
                .andExpect(jsonPath("$.correo").value("nuevo@correo.pe"))
                .andExpect(jsonPath("$.telefono").value("912345678"))
                .andExpect(jsonPath("$.dni").value("70123456"));

        datos.put("correo", "ocupado@correo.pe");
        mvc.perform(conJson(put("/api/auth/clientes/yo").header("Authorization", bearer(c)), datos))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_REGISTRADO"));

        datos.put("telefono", "123");
        datos.put("correo", "");
        mvc.perform(conJson(put("/api/auth/clientes/yo").header("Authorization", bearer(c)), datos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo").value(hasItems("telefono", "correo")));
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).contains("EDITAR_CUENTA");
    }

    @Test
    void cambiarContrasena() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        String url = "/api/auth/clientes/cambiar-contrasena";
        mvc.perform(conJson(post(url).header("Authorization", bearer(c)),
                        Map.of("actual", "Equivocada1", "nueva", "Nueva2026x", "confirmacion", "Nueva2026x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("actual"));
        mvc.perform(conJson(post(url).header("Authorization", bearer(c)),
                        Map.of("actual", CLAVE_CLIENTE, "nueva", CLAVE_CLIENTE, "confirmacion", CLAVE_CLIENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nueva"));
        mvc.perform(conJson(post(url).header("Authorization", bearer(c)),
                        Map.of("actual", CLAVE_CLIENTE, "nueva", "Nueva2026x", "confirmacion", "Nueva2026x")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña se cambió correctamente."));
        login("70123456", "Nueva2026x").andExpect(status().isOk());
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).contains("CAMBIAR_CONTRASENA");
    }

    @Test
    void permisosCruzadosEntreAdministradoresYClientes() throws Exception {
        CuentaCliente c = crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);

        // Sin token
        mvc.perform(get("/api/auth/clientes/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        // Administrador en rutas de clientes
        mvc.perform(get("/api/auth/clientes/yo").header("Authorization", bearer(rosa)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // Cliente en rutas del panel
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(c)))
                .andExpect(status().isForbidden());
        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(c)),
                        Map.of("actual", CLAVE_CLIENTE, "nueva", "Nueva2026x", "confirmacion", "Nueva2026x")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(c)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bitacora").header("Authorization", bearer(c)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // El administrador sigue entrando a lo suyo
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(rosa))).andExpect(status().isOk());
    }

    @Test
    void elLoginDelPanelNoAceptaCuentasDeClientes() throws Exception {
        crearCuenta("70123456", "lucia@correo.pe", CLAVE_CLIENTE);
        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", "lucia@correo.pe", "contrasena", CLAVE_CLIENTE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        // Solo POST es público: un GET a la ruta de registro pide sesión.
        mvc.perform(get("/api/auth/clientes/registro")).andExpect(status().isUnauthorized());
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).doesNotContain("REGISTRAR_CUENTA");
    }
}
