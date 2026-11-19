package pe.alcanfores.auth.controlador;

import org.junit.jupiter.api.Test;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.Rol;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Celular opcional de los usuarios del panel: en /yo, PUT /yo y en crear/editar usuarios. */
class TelefonoAdminIntegracionTest extends PruebaIntegracion {

    private static Map<String, Object> misDatos(String telefono, String correo) {
        Map<String, Object> m = new HashMap<>();
        m.put("telefono", telefono);
        m.put("correo", correo);
        return m;
    }

    @Test
    void elAdminCompletaSuCelularYSuCorreo() throws Exception {
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(rosa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").doesNotExist());

        mvc.perform(conJson(put("/api/auth/yo"), misDatos("+51 987 654 254", "Rosa.H@Alcanfores.pe"))
                        .header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("987654254"))
                .andExpect(jsonPath("$.correo").value("rosa.h@alcanfores.pe"));
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(rosa)))
                .andExpect(jsonPath("$.telefono").value("987654254"));
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion).containsExactly("EDITAR_MIS_DATOS");
        assertThat(bitacoras.findAll().get(0).getDetalle()).contains("telefono: - -> 987654254");

        mvc.perform(conJson(put("/api/auth/yo"), misDatos("", "rosa.h@alcanfores.pe")).header("Authorization", bearer(rosa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").doesNotExist());
        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", "rhuaman", "contrasena", CLAVE)))
                .andExpect(jsonPath("$.usuario.correo").value("rosa.h@alcanfores.pe"));
    }

    @Test
    void misDatosValidaCelularYCorreoYNoAceptaUnCorreoAjeno() throws Exception {
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);
        crearUsuario("mtorres", Rol.RECEPCION);
        mvc.perform(conJson(put("/api/auth/yo"), misDatos("812345678", "x")).header("Authorization", bearer(rosa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("telefono", "correo")));
        mvc.perform(conJson(put("/api/auth/yo"), misDatos(null, "mtorres@alcanfores.pe")).header("Authorization", bearer(rosa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        mvc.perform(conJson(put("/api/auth/yo"), misDatos(null, "a@b.pe")).header("Authorization",
                        bearer(crearCuenta("70123456", "lucia@correo.pe", "Futbol2026"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearYEditarUsuariosConCelularOpcional() throws Exception {
        Administrador rosa = crearUsuario("rhuaman", Rol.ADMIN);
        Map<String, Object> nuevo = new HashMap<>();
        nuevo.put("nombres", "Marco");
        nuevo.put("apellidos", "Torres");
        nuevo.put("usuario", "mtorres");
        nuevo.put("correo", "mtorres@alcanfores.pe");
        nuevo.put("rol", "RECEPCION");
        nuevo.put("contrasena", "Recepcion2026");
        nuevo.put("telefono", "912 345 678");
        String r = mvc.perform(conJson(post("/api/admin/usuarios"), nuevo).header("Authorization", bearer(rosa)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.telefono").value("912345678"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(r).get("id").asLong();

        Map<String, Object> edicion = new HashMap<>(nuevo);
        edicion.remove("contrasena");
        edicion.put("telefono", "12345");
        mvc.perform(conJson(put("/api/admin/usuarios/" + id), edicion).header("Authorization", bearer(rosa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
        edicion.put("telefono", null);
        mvc.perform(conJson(put("/api/admin/usuarios/" + id), edicion).header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").doesNotExist());
        assertThat(bitacoras.findAll()).anySatisfy(b -> assertThat(b.getDetalle()).contains("telefono: 912345678 -> null"));
    }
}
