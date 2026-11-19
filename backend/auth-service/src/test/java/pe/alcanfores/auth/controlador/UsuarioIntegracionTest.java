package pe.alcanfores.auth.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.util.Tiempo;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioIntegracionTest extends PruebaIntegracion {

    private Administrador rosa;
    private Administrador marco;
    private Administrador docente;

    @BeforeEach
    void datos() {
        rosa = crearUsuario("rhuaman", Rol.ADMIN);
        marco = crearUsuario("mtorres", Rol.RECEPCION);
        docente = crearUsuario("docente", Rol.ADMIN);
    }

    private Map<String, Object> nuevoUsuario() {
        Map<String, Object> m = new HashMap<>();
        m.put("nombres", "  ana   lucía ");
        m.put("apellidos", "quispe ñahui");
        m.put("usuario", "aquispe");
        m.put("correo", "AQuispe@Alcanfores.pe");
        m.put("rol", "RECEPCION");
        m.put("contrasena", "Recepcion2026");
        return m;
    }

    private Map<String, Object> edicion(Administrador a, Rol rol) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombres", a.getNombres());
        m.put("apellidos", a.getApellidos());
        m.put("usuario", a.getUsuario());
        m.put("correo", a.getCorreo());
        m.put("rol", rol.name());
        return m;
    }

    @Test
    void adminListaUsuariosConFiltrosYPaginacion() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.contenido[0].contrasenaHash").doesNotExist());

        mvc.perform(get("/api/admin/usuarios").param("rol", "ADMIN").param("q", "HUAM")
                        .header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].usuario").value("rhuaman"));

        mvc.perform(get("/api/admin/usuarios").param("size", "500").param("sort", "contrasenaHash,desc")
                        .header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanio").value(100));
    }

    @Test
    void recepcionNoPuedeGestionarUsuarios() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(marco)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(marco)), nuevoUsuario()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void crearUsuarioNormalizaDatosYQuedaEnLaBitacora() throws Exception {
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(rosa)), nuevoUsuario()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/usuarios/")))
                .andExpect(jsonPath("$.nombres").value("Ana Lucía"))
                .andExpect(jsonPath("$.apellidos").value("Quispe Ñahui"))
                .andExpect(jsonPath("$.correo").value("aquispe@alcanfores.pe"))
                .andExpect(jsonPath("$.rol").value("RECEPCION"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        Bitacora b = bitacoras.findAll().get(0);
        assertThat(b.getAccion()).isEqualTo("CREAR_USUARIO");
        assertThat(b.getActor()).isEqualTo("rhuaman");
        assertThat(b.getIdActor()).isEqualTo(rosa.getId());
        assertThat(b.getServicio()).isEqualTo("auth-service");
        Administrador creado = administradores.findAll().stream().filter(a -> a.getUsuario().equals("aquispe")).findFirst().orElseThrow();
        assertThat(codificador.matches("Recepcion2026", creado.getContrasenaHash())).isTrue();
    }

    @Test
    void usuarioYCorreoRepetidosResponden409() throws Exception {
        Map<String, Object> repetido = nuevoUsuario();
        repetido.put("usuario", "mtorres");
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(rosa)), repetido))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DUPLICADO"));

        Map<String, Object> correo = nuevoUsuario();
        correo.put("correo", "MTORRES@alcanfores.pe");
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(rosa)), correo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
    }

    @Test
    void crearUsuarioValidaCadaCampo() throws Exception {
        Map<String, Object> malo = new HashMap<>();
        malo.put("nombres", "R2D2");
        malo.put("apellidos", "");
        malo.put("usuario", "MT");
        malo.put("correo", "correo@sinpunto");
        malo.put("contrasena", "corta");
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(rosa)), malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("nombres", "apellidos", "usuario", "correo", "rol", "contrasena")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("Usa solo letras (2 a 60).",
                        "Usuario de 4 a 30 caracteres en minúsculas.", "Elige un rol.")));
    }

    @Test
    void rolInexistenteResponde400() throws Exception {
        Map<String, Object> m = nuevoUsuario();
        m.put("rol", "JEFE");
        mvc.perform(conJson(post("/api/admin/usuarios").header("Authorization", bearer(rosa)), m))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("rol"));
    }

    @Test
    void adminNoPuedeQuitarseSuPropioRol() throws Exception {
        mvc.perform(conJson(put("/api/admin/usuarios/{id}", rosa.getId()).header("Authorization", bearer(rosa)),
                        edicion(rosa, Rol.RECEPCION)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("AUTOPROTECCION"))
                .andExpect(jsonPath("$.mensaje").value("No puedes quitarte tu propio rol de administrador."));
    }

    @Test
    void adminPuedeEditarSusDatosSinPerderElRol() throws Exception {
        Map<String, Object> m = edicion(rosa, Rol.ADMIN);
        m.put("correo", "rosa.huaman@alcanfores.pe");
        mvc.perform(conJson(put("/api/admin/usuarios/{id}", rosa.getId()).header("Authorization", bearer(rosa)), m))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("rosa.huaman@alcanfores.pe"));
    }

    @Test
    void editarOtroUsuarioCambiaRolYAnotaLosCambios() throws Exception {
        mvc.perform(conJson(put("/api/admin/usuarios/{id}", marco.getId()).header("Authorization", bearer(rosa)),
                        edicion(marco, Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));

        Bitacora b = bitacoras.findAll().get(0);
        assertThat(b.getAccion()).isEqualTo("EDITAR_USUARIO");
        assertThat(b.getDetalle()).contains("rol: RECEPCION -> ADMIN");
    }

    @Test
    void adminPuedeQuitarElRolAdminAOtroSiQuedaOtroAdmin() throws Exception {
        mvc.perform(conJson(put("/api/admin/usuarios/{id}", docente.getId()).header("Authorization", bearer(rosa)),
                        edicion(docente, Rol.RECEPCION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("RECEPCION"));
    }

    @Test
    void editarConUsuarioDeOtroResponde409() throws Exception {
        Map<String, Object> m = edicion(marco, Rol.RECEPCION);
        m.put("usuario", "docente");
        mvc.perform(conJson(put("/api/admin/usuarios/{id}", marco.getId()).header("Authorization", bearer(rosa)), m))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_DUPLICADO"));
    }

    @Test
    void usuarioInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/admin/usuarios/{id}", 999_999).header("Authorization", bearer(rosa)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void adminNoPuedeDesactivarse() throws Exception {
        mvc.perform(conJson(patch("/api/admin/usuarios/{id}/estado", rosa.getId()).header("Authorization", bearer(rosa)),
                        Map.of("estado", "INACTIVO")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("AUTOPROTECCION"))
                .andExpect(jsonPath("$.mensaje").value("No puedes desactivar tu propio usuario."));
    }

    @Test
    void desactivarYReactivarOtroUsuario() throws Exception {
        mvc.perform(conJson(patch("/api/admin/usuarios/{id}/estado", marco.getId()).header("Authorization", bearer(rosa)),
                        Map.of("estado", "INACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", "mtorres", "contrasena", CLAVE)))
                .andExpect(status().isForbidden());

        mvc.perform(conJson(patch("/api/admin/usuarios/{id}/estado", marco.getId()).header("Authorization", bearer(rosa)),
                        Map.of("estado", "ACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        assertThat(bitacoras.findAll()).extracting(Bitacora::getAccion)
                .contains("DESACTIVAR_USUARIO", "ACTIVAR_USUARIO");
    }

    @Test
    void estadoObligatorio() throws Exception {
        mvc.perform(conJson(patch("/api/admin/usuarios/{id}/estado", marco.getId()).header("Authorization", bearer(rosa)),
                        Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
    }

    @Test
    void restablecerContrasenaDesbloqueaLaCuenta() throws Exception {
        marco.setBloqueadoHasta(Tiempo.ahora().plusMinutes(10));
        marco.setIntentosFallidos(0);
        administradores.save(marco);

        mvc.perform(conJson(post("/api/admin/usuarios/{id}/restablecer-contrasena", marco.getId())
                        .header("Authorization", bearer(rosa)), Map.of("nueva", "Temporal2026")))
                .andExpect(status().isOk());

        mvc.perform(conJson(post("/api/auth/login"), Map.of("usuario", "mtorres", "contrasena", "Temporal2026")))
                .andExpect(status().isOk());
        assertThat(bitacoras.findAll()).extracting(Bitacora::getDetalle)
                .anyMatch(d -> d != null && d.contains("cuenta desbloqueada"));
    }

    @Test
    void restablecerContrasenaExigeUnaClaveSegura() throws Exception {
        mvc.perform(conJson(post("/api/admin/usuarios/{id}/restablecer-contrasena", marco.getId())
                        .header("Authorization", bearer(rosa)), Map.of("nueva", "12345678")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nueva"));
    }

    @Test
    void usuarioInactivoSigueApareciendoEnLaLista() throws Exception {
        marco.setEstado(EstadoUsuario.INACTIVO);
        administradores.save(marco);
        mvc.perform(get("/api/admin/usuarios").param("estado", "INACTIVO").header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].usuario").value("mtorres"));
    }
}
