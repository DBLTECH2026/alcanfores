package pe.alcanfores.auth.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacionIntegracionTest extends PruebaIntegracion {

    private ResultActions login(String usuario, String contrasena) throws Exception {
        return mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", usuario, "contrasena", contrasena)));
    }

    @Test
    void loginCorrectoDevuelveTokenYDatosDelUsuario() throws Exception {
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);

        String cuerpo = login("  RHuaman ", CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario.id").value(rosa.getId()))
                .andExpect(jsonPath("$.usuario.usuario").value("rhuaman"))
                .andExpect(jsonPath("$.usuario.correo").value("rhuaman@alcanfores.pe"))
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.contrasenaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode r = json.readTree(cuerpo);
        UsuarioToken t = jwt.validar(r.get("token").asText());
        assertThat(t.id()).isEqualTo(rosa.getId());
        assertThat(t.rol()).isEqualTo("ADMIN");
        assertThat(t.nombre()).isEqualTo("Nombre rhuaman Apellido");
        Administrador guardado = administradores.findById(rosa.getId()).orElseThrow();
        assertThat(guardado.getUltimoAcceso()).isNotNull();
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).containsExactly("LOGIN_EXITOSO");
    }

    @Test
    void contrasenaIncorrectaResponde401YCuentaElIntento() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        login("mtorres", "Otra2026x")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos."))
                .andExpect(jsonPath("$.ruta").value("/api/auth/login"));

        assertThat(administradores.findById(marco.getId()).orElseThrow().getIntentosFallidos()).isEqualTo(1);
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).containsExactly("LOGIN_FALLIDO");
    }

    @Test
    void usuarioInexistenteRespondeIgualQueClaveIncorrecta() throws Exception {
        login("nadie.existe", "Cualquiera1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void cincoFallosBloqueanQuinceMinutosYLuegoSePuedeEntrar() throws Exception {
        crearUsuario("rhuaman", Rol.ADMIN);
        for (int i = 0; i < 4; i++) {
            login("rhuaman", "Equivocada1").andExpect(status().isUnauthorized());
        }
        login("rhuaman", "Equivocada1")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("USUARIO_BLOQUEADO"))
                .andExpect(jsonPath("$.mensaje").value(containsString("15 minutos")));

        // Aun con la clave correcta sigue bloqueado.
        login("rhuaman", CLAVE).andExpect(status().isLocked());

        Tiempo.fijar(Clock.offset(Clock.system(Tiempo.ZONA), Duration.ofMinutes(10)));
        login("rhuaman", CLAVE)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.mensaje").value(containsString("5 minutos")));

        Tiempo.fijar(Clock.offset(Clock.system(Tiempo.ZONA), Duration.ofMinutes(16)));
        login("rhuaman", CLAVE).andExpect(status().isOk());
        Administrador a = administradores.findAll().get(0);
        assertThat(a.getIntentosFallidos()).isZero();
        assertThat(a.getBloqueadoHasta()).isNull();
    }

    @Test
    void unUsuarioInexistenteTambienSeBloqueaParaNoDelatarCuentas() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("fantasma.uno", "Equivocada1").andExpect(status().isUnauthorized());
        }
        login("fantasma.uno", "Equivocada1").andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("USUARIO_BLOQUEADO"));
        login("fantasma.uno", "Equivocada1").andExpect(status().isLocked());
    }

    @Test
    void loginValidaCamposObligatorios() throws Exception {
        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", " ", "contrasena", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("usuario", "contrasena")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("Ingresa tu usuario.", "Ingresa tu contraseña.")));
    }

    @Test
    void loginConJsonMalFormadoResponde400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{usuario:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
    }

    @Test
    void usuarioDesactivadoNoPuedeEntrar() throws Exception {
        Administrador a = crearUsuario("inactivo", Rol.RECEPCION);
        a.setEstado(EstadoUsuario.INACTIVO);
        administradores.save(a);

        login("inactivo", CLAVE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
    }

    @Test
    void yoDevuelveElUsuarioDelToken() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(marco)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(marco.getId()))
                .andExpect(jsonPath("$.usuario").value("mtorres"))
                .andExpect(jsonPath("$.rol").value("RECEPCION"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.bloqueado").value(false));
    }

    @Test
    void yoSinTokenResponde401() throws Exception {
        mvc.perform(get("/api/auth/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Inicia sesión para continuar."));
    }

    @Test
    void yoConTokenVencidoAvisaQueLaSesionVencio() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(get("/api/auth/yo").header("Authorization", bearerVencido(marco)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Tu sesión venció. Vuelve a iniciar sesión."));
    }

    @Test
    void yoConTokenAlteradoResponde401() throws Exception {
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token de sesión no es válido."));
    }

    @Test
    void yoConUsuarioDesactivadoDespuesDelLoginResponde401() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);
        String token = bearer(marco);
        marco.setEstado(EstadoUsuario.INACTIVO);
        administradores.save(marco);

        mvc.perform(get("/api/auth/yo").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void cambiarContrasenaCorrectamentePermiteEntrarConLaNueva() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(marco)),
                        Map.of("actual", CLAVE, "nueva", "NuevaClave2027", "confirmacion", "NuevaClave2027")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña se cambió correctamente."));

        login("mtorres", CLAVE).andExpect(status().isUnauthorized());
        login("mtorres", "NuevaClave2027").andExpect(status().isOk());
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).contains("CAMBIAR_CONTRASENA");
    }

    @Test
    void cambiarContrasenaConActualIncorrectaMarcaElCampo() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(marco)),
                        Map.of("actual", "NoEsLaMia1", "nueva", "NuevaClave2027", "confirmacion", "NuevaClave2027")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("actual"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña actual no es correcta."));
    }

    @Test
    void cambiarContrasenaAplicaLasReglasDeSeguridad() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(marco)),
                        Map.of("actual", CLAVE, "nueva", "sinmayuscula1", "confirmacion", "sinmayuscula1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nueva"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(
                        "La contraseña debe tener de 8 a 64 caracteres, con al menos una mayúscula, una minúscula y un número."));
    }

    @Test
    void cambiarContrasenaExigeQueLaConfirmacionCoincida() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(marco)),
                        Map.of("actual", CLAVE, "nueva", "NuevaClave2027", "confirmacion", "NuevaClave2028")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("confirmacion")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItem("Las contraseñas no coinciden.")));
    }

    @Test
    void laContrasenaNuevaDebeSerDistintaDeLaActual() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);

        mvc.perform(conJson(post("/api/auth/cambiar-contrasena").header("Authorization", bearer(marco)),
                        Map.of("actual", CLAVE, "nueva", CLAVE, "confirmacion", CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nueva"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña nueva debe ser distinta de la actual."));
    }

    @Test
    void cambiarContrasenaSinTokenResponde401() throws Exception {
        mvc.perform(conJson(post("/api/auth/cambiar-contrasena"),
                        Map.of("actual", CLAVE, "nueva", "NuevaClave2027", "confirmacion", "NuevaClave2027")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutasDesconocidasQuedanCerradas() throws Exception {
        mvc.perform(get("/api/otra-cosa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
