package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.alcanfores.catalogo.cliente.ElegibilidadResena;
import pe.alcanfores.comun.error.NegocioExcepcion;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reseñas verificadas con reservas-service (simulado), resumen y moderación. Usa las canchas 2 y 3. */
class ResenaTest extends PruebaIntegracion {

    static String resena(String codigo, String dni, int puntuacion, String comentario) {
        return """
                {"codigoReserva": "%s", "dni": "%s", "puntuacion": %d, "comentario": "%s"}
                """.formatted(codigo, dni, puntuacion, comentario);
    }

    @Test
    void reservaElegibleCreaLaResenaYActualizaElResumen() throws Exception {
        when(reservas.elegibleResena("ALC-QW3RT7", "45678912", 2L))
                .thenReturn(new ElegibilidadResena(true, null, "Carla M."));

        mvc.perform(post("/api/catalogo/canchas/2/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("alc-qw3rt7", "45678912", 1, "Llegamos y la cancha estaba ocupada por otro grupo.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor").value("Carla M."))
                .andExpect(jsonPath("$.puntuacion").value(1))
                .andExpect(jsonPath("$.verificada").value(true))
                .andExpect(jsonPath("$.fecha", notNullValue()));

        mvc.perform(get("/api/catalogo/canchas/cancha-2-futbol-6"))
                .andExpect(jsonPath("$.resumenResenas.total").value(6))
                .andExpect(jsonPath("$.resumenResenas.distribucion.1").value(1))
                .andExpect(jsonPath("$.resumenResenas.promedio").value(3.7)); // (5+4+3+5+4+1)/6 = 3.67 → 3.7
    }

    @Test
    void reservaNoElegibleDa422ConElMotivoDeReservas() throws Exception {
        when(reservas.elegibleResena(anyString(), anyString(), anyLong()))
                .thenReturn(new ElegibilidadResena(false, "Podrás calificar la cancha cuando termine tu partido.", null));
        mvc.perform(post("/api/catalogo/canchas/3/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-ZZ7K4P", "45678912", 5, "Excelente cancha, muy bien iluminada.")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESENA_NO_ELEGIBLE"))
                .andExpect(jsonPath("$.mensaje").value("Podrás calificar la cancha cuando termine tu partido."));
    }

    @Test
    void reservaYaResenadaDa409SinConsultarAReservas() throws Exception {
        mvc.perform(post("/api/catalogo/canchas/2/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-K5T8VZ", "45678912", 4, "Quiero dejar otra reseña de la misma reserva.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESENA_DUPLICADA"));
        verify(reservas, never()).elegibleResena(anyString(), anyString(), any());
    }

    @Test
    void reservasCaidoDa503() throws Exception {
        when(reservas.elegibleResena(anyString(), anyString(), anyLong()))
                .thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                        "El servicio de reservas no responde. Intenta en unos segundos."));
        mvc.perform(post("/api/catalogo/canchas/3/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-ZZ7K4Q", "45678912", 5, "Excelente cancha, muy bien iluminada.")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    void camposInvalidosDan400PorCampo() throws Exception {
        mvc.perform(post("/api/catalogo/canchas/3/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-1234", "1234", 6, "Visiten www.otracancha.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("codigoReserva", "dni", "puntuacion", "comentario")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("El código tiene el formato ALC-XXXXXX.",
                        "El DNI debe tener 8 dígitos.", "Elige una calificación de 1 a 5 estrellas.",
                        "Escribe entre 10 y 500 caracteres, sin enlaces.")));
        mvc.perform(post("/api/catalogo/canchas/3/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-QW3RT8", "45678912", 2, "PESIMO SERVICIO NO VUELVO NUNCA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("comentario"));
        verify(reservas, never()).elegibleResena(anyString(), anyString(), any());
    }

    @Test
    void canchaInexistenteDa404() throws Exception {
        mvc.perform(post("/api/catalogo/canchas/9999/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(resena("ALC-QW3RT9", "45678912", 5, "Excelente cancha, muy bien iluminada.")))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderacionOcultarResponderYPermisos() throws Exception {
        String json = mvc.perform(get("/api/admin/resenas").header("Authorization", tokenAdmin())
                        .param("idCancha", "3").param("puntuacion", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].autor").value("Fiorella C."))
                .andExpect(jsonPath("$.contenido[0].codigoReserva").value("ALC-X6D3MK"))
                .andExpect(jsonPath("$.contenido[0].cancha").value("Cancha 3 · Fútbol 7"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.contenido[0].id");

        mvc.perform(patch("/api/admin/resenas/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"OCULTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OCULTA"));
        mvc.perform(get("/api/catalogo/canchas/3"))
                .andExpect(jsonPath("$.resumenResenas.total").value(4))
                .andExpect(jsonPath("$.resumenResenas.distribucion.3").value(0));
        mvc.perform(get("/api/admin/resenas").header("Authorization", tokenAdmin()).param("estado", "OCULTA"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mvc.perform(post("/api/admin/resenas/" + id + "/respuesta").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"Gracias\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La respuesta debe tener entre 10 y 500 caracteres."));
        mvc.perform(post("/api/admin/resenas/" + id + "/respuesta").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Gracias, Fiorella. Ya ampliamos el estacionamiento.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Gracias, Fiorella. Ya ampliamos el estacionamiento."))
                .andExpect(jsonPath("$.respondidoEn", notNullValue()));

        mvc.perform(patch("/api/admin/resenas/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADA\"}"))
                .andExpect(status().isOk());
        verify(publicador, org.mockito.Mockito.atLeast(3)).publicar(eq("auditoria.accion"), any());

        mvc.perform(get("/api/admin/resenas").header("Authorization", tokenRecepcion())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/resenas/" + id).header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"OCULTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/resenas/999999").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"OCULTA\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/resenas").header("Authorization", tokenAdmin()).param("puntuacion", "9"))
                .andExpect(status().isBadRequest());
    }
}
