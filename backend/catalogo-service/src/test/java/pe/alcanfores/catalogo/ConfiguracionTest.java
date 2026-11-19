package pe.alcanfores.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Configuración del complejo: lectura con token, edición solo ADMIN y validación por clave. */
class ConfiguracionTest extends PruebaIntegracion {

    static final String INTERNO = "token-interno-solo-para-desarrollo-local";

    @Test
    void recepcionPuedeVerPeroNoEditar() throws Exception {
        mvc.perform(get("/api/admin/configuracion").header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(23)))
                .andExpect(jsonPath("$.adelanto_pct").value("50"))
                .andExpect(jsonPath("$.ultimo_turno").value("23:00"));
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lema\":\"Otro lema del complejo\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/configuracion")).andExpect(status().isUnauthorized());
    }

    @Test
    void valoresInvalidosDan400ConErrorPorClave() throws Exception {
        String cuerpo = """
                {"adelanto_pct": 150, "primer_turno": "8am", "yape_numero": "12345", "correo": "informes@",
                 "ruc_emisor": "20612345678", "clave_rara": "x", "mapa_url": "http://mapa.com", "latitud": "abc",
                 "dias_visibles": "0", "lema": "", "direccion": "<script>alert(1)</script>"}
                """;
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("adelanto_pct", "primer_turno", "yape_numero", "correo",
                        "ruc_emisor", "clave_rara", "mapa_url", "latitud", "dias_visibles", "lema", "direccion")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("Ingresa un porcentaje entero entre 0 y 100.",
                        "Usa el formato de hora HH:mm (por ejemplo, 08:00).",
                        "Ingresa un celular peruano de 9 dígitos que empiece con 9.", "El RUC no es válido.",
                        "Esta clave de configuración no existe.", "Este dato es obligatorio.")));
        verify(publicador, never()).publicar(eq("auditoria.accion"), any());
        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(jsonPath("$.adelanto_pct").value("50"));
    }

    @Test
    void horarioIncoherenteDa400EnLaClaveQueChoca() throws Exception {
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inicio_noche\": \"07:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("inicio_noche"));
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"primer_turno\": \"23:30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("ultimo_turno"));
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"duracion_min\": 50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La duración debe ser múltiplo de 15 minutos."));
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiosValidosSeNormalizanGuardanYAuditan() throws Exception {
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plin_numero\": \"+51 987 654 321\", \"recordatorio_horas\": 3, "
                                + "\"referencia\": \"  Paradero   Caseta, frente al grifo \", \"correo\": \"Informes@Alcanfores.PE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plin_numero").value("987654321"))
                .andExpect(jsonPath("$.recordatorio_horas").value("3"))
                .andExpect(jsonPath("$.referencia").value("Paradero Caseta, frente al grifo"))
                .andExpect(jsonPath("$.correo").value("informes@alcanfores.pe"));
        verify(publicador).publicar(eq("auditoria.accion"), any());

        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plin_numero").value("987654321"));
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(jsonPath("$.reglas.recordatorioHoras").value(3))
                .andExpect(jsonPath("$.pagos.plinNumero").value("987654321"));
        mvc.perform(get("/interno/configuracion")).andExpect(status().isForbidden());
    }
}
