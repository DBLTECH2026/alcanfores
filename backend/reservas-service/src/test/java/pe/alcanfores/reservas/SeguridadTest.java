package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 401 sin token, 403 por rol y rutas internas protegidas por X-Interno-Token. */
class SeguridadTest extends PruebaIntegracion {

    @Test
    void elPanelSinTokenResponde401ConElCuerpoEstandar() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Inicia sesión para continuar."));
        mvc.perform(get("/api/admin/reservas").header("Authorization", "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token de sesión no es válido."));
    }

    @Test
    void recepcionNoVeReportesNiGeneraTurnos() throws Exception {
        mvc.perform(get("/api/admin/reportes/reservas").header("Authorization", token("RECEPCION")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(get("/api/admin/reportes/reservas.xlsx").header("Authorization", token("RECEPCION")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/turnos/generar").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"desde\":\"2026-11-20\",\"hasta\":\"2026-11-21\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recepcionSiVeElDashboardYLaAgenda() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header("Authorization", token("RECEPCION"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/turnos").param("fecha", HOY.toString()).header("Authorization", token("RECEPCION")))
                .andExpect(status().isOk());
    }

    @Test
    void lasRutasInternasExigenElTokenInterno() throws Exception {
        mvc.perform(get("/interno/reservas/ALC-AAAAAA"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Ruta interna: solo para otros microservicios."));
        mvc.perform(get("/interno/reservas/ALC-AAAAAA").header("Authorization", token("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/interno/reservas/ALC-AAAAAA").header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isNotFound());
    }

    @Test
    void elPortalEsPublicoYLoDemasSeNiega() throws Exception {
        mvc.perform(get("/api/disponibilidad").param("fecha", HOY.toString())).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/otra-cosa")).andExpect(status().isUnauthorized());
    }
}
