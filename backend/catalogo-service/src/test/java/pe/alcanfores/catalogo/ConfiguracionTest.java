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
                .andExpect(jsonPath("$", aMapWithSize(28)))
                .andExpect(jsonPath("$.dias_generados").value("120"))
                .andExpect(jsonPath("$.adelanto_pct").value("50"))
                .andExpect(jsonPath("$.ultimo_turno").value("23:00"))
                // V3__ruc_emisor_ficticio: el RUC anterior (20612345679) existe en SUNAT; el emisor ficticio es 20000000001.
                .andExpect(jsonPath("$.ruc_emisor").value("20000000001"));
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
                        .content("{\"plin_numero\": \"+51 987 654 321\", \"recordatorio_minutos\": \" 15, 180 \", "
                                + "\"referencia\": \"  Paradero   Caseta, frente al grifo \", \"correo\": \"Informes@Alcanfores.PE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plin_numero").value("987654321"))
                .andExpect(jsonPath("$.recordatorio_minutos").value("180,15"))
                .andExpect(jsonPath("$.referencia").value("Paradero Caseta, frente al grifo"))
                .andExpect(jsonPath("$.correo").value("informes@alcanfores.pe"));
        verify(publicador).publicar(eq("auditoria.accion"), any());

        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plin_numero").value("987654321"))
                .andExpect(jsonPath("$.recordatorio_minutos").value("180,15"));
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos[0]").value(180))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos[1]").value(15))
                .andExpect(jsonPath("$.reglas.recordatorioHoras").value(3))
                .andExpect(jsonPath("$.pagos.plinNumero").value("987654321"));
        mvc.perform(get("/interno/configuracion")).andExpect(status().isForbidden());
    }

    @Test
    void laMigracionV5DejaLosMinutosDelRecordatorioYLaAsistencia() throws Exception {
        // V5 convierte recordatorio_horas (2 en los datos demo) a minutos y borra la clave vieja.
        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorio_horas").doesNotExist())
                .andExpect(jsonPath("$.recordatorio_minutos").isString())
                .andExpect(jsonPath("$.asistencia_desde_min").isString());
    }

    @Test
    void listaDeRecordatoriosInvalidaDa400EnSuClave() throws Exception {
        String mensaje = "Escribe de 1 a 3 tiempos en minutos (5 a 1440), separados por comas y sin repetir.";
        for (String malo : new String[]{"\"\"", "\"120,60,30,15\"", "\"4\"", "\"1441\"", "\"60,60\"", "\"dos horas\"",
                "\"60,,15\"", "\"-15\"", "\"60.5\""}) {
            mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"recordatorio_minutos\": " + malo + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("recordatorio_minutos"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(malo.equals("\"\"") ? "Este dato es obligatorio." : mensaje));
        }
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asistencia_desde_min\": 181}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("asistencia_desde_min"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa un número entero entre 0 y 180."));
    }

    @Test
    void recordatoriosValidosSeOrdenanYElComplejoLosPublica() throws Exception {
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordatorio_minutos\": \"15,1440, 60\", \"asistencia_desde_min\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorio_minutos").value("1440,60,15"))
                .andExpect(jsonPath("$.asistencia_desde_min").value("0"));
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos.length()").value(3))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos[0]").value(1440))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos[2]").value(15))
                .andExpect(jsonPath("$.reglas.recordatorioHoras").value(24))
                .andExpect(jsonPath("$.reglas.asistenciaDesdeMin").value(0));
        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recordatorio_minutos\": 45, \"asistencia_desde_min\": \"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorio_minutos").value("45"));
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(jsonPath("$.reglas.recordatorioMinutos[0]").value(45))
                .andExpect(jsonPath("$.reglas.recordatorioHoras").value(0))
                .andExpect(jsonPath("$.reglas.asistenciaDesdeMin").value(30));
    }

    @Test
    void interruptoresDeCanchitaConValoresPorDefectoValidadosYPublicos() throws Exception {
        // V6__asistente_ia: WhatsApp apagado hasta que el dueño lo encienda; web y validación de pagos encendidas.
        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ia_whatsapp_activa").value("false"))
                .andExpect(jsonPath("$.ia_web_activa").value("true"))
                .andExpect(jsonPath("$.ia_valida_pagos").value("true"));
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistente.webActiva").value(true))
                .andExpect(jsonPath("$.asistente.whatsappActiva").doesNotExist());

        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ia_whatsapp_activa\": \"si\", \"ia_web_activa\": 1, \"ia_valida_pagos\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("ia_whatsapp_activa", "ia_web_activa", "ia_valida_pagos")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("Elige encendido o apagado (true o false).",
                        "Este dato es obligatorio.")));

        mvc.perform(put("/api/admin/configuracion").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ia_whatsapp_activa\": true, \"ia_web_activa\": \"FALSE\", \"ia_valida_pagos\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ia_whatsapp_activa").value("true"))
                .andExpect(jsonPath("$.ia_web_activa").value("false"))
                .andExpect(jsonPath("$.ia_valida_pagos").value("false"));
        verify(publicador).publicar(eq("auditoria.accion"), any());
        mvc.perform(get("/interno/configuracion").header("X-Interno-Token", INTERNO))
                .andExpect(jsonPath("$.ia_whatsapp_activa").value("true"))
                .andExpect(jsonPath("$.ia_valida_pagos").value("false"));
        mvc.perform(get("/api/catalogo/complejo")).andExpect(jsonPath("$.asistente.webActiva").value(false));
    }
}
