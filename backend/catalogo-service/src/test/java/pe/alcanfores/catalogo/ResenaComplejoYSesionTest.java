package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.alcanfores.catalogo.cliente.ElegibilidadResena;
import pe.alcanfores.comun.util.Tiempo;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reseñas v2 (SPEC_V2 §E): la del cliente exige sesión (DNI del token) y el administrador gestiona las del complejo desde
 * cada cancha. Usa canchas nuevas para no mover los promedios que revisan otras pruebas.
 */
class ResenaComplejoYSesionTest extends PruebaIntegracion {

    static String resenaComplejo(String autor, int puntuacion, String comentario, String fecha) {
        return """
                {"autor": "%s", "puntuacion": %d, "comentario": "%s", "fecha": "%s"}
                """.formatted(autor, puntuacion, comentario, fecha);
    }

    long canchaNueva() throws Exception {
        String json = mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CanchaAdminTest.cuerpo(CanchaAdminTest.nombreUnico(), "50.00", "70.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void sinSesionDa401ConMensajeYConTokenDeAdministrador403AntesDeValidarLosCampos() throws Exception {
        mvc.perform(post("/api/catalogo/canchas/2/resenas").contentType(MediaType.APPLICATION_JSON)
                        .content(ResenaTest.resena("ALC-1234", 9, "corto")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Inicia sesión para calificar la cancha."));
        mvc.perform(post("/api/catalogo/canchas/2/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ResenaTest.resena("ALC-QW3RT7", 5, "Excelente cancha, muy bien iluminada.")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        verify(reservas, never()).elegibleResena(anyString(), anyString(), anyLong());
    }

    @Test
    void elClienteConSesionCalificaConElDniDeSuToken() throws Exception {
        long id = canchaNueva();
        when(reservas.elegibleResena("ALC-MN8PQ2", "70123456", id)).thenReturn(new ElegibilidadResena(true, null, "Lucía M."));
        mvc.perform(post("/api/catalogo/canchas/" + id + "/resenas").header("Authorization", tokenCliente("70123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\": \"ALC-MN8PQ2\", \"dni\": \"99999999\", \"puntuacion\": 5,"
                                + " \"comentario\": \"Muy buena cancha y buena atención.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor").value("Lucía M."))
                .andExpect(jsonPath("$.verificada").value(true))
                .andExpect(jsonPath("$.origen").value("CLIENTE"));
        mvc.perform(get("/api/admin/canchas/" + id + "/resenas").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$[0].codigoReserva").value("ALC-MN8PQ2"))
                .andExpect(jsonPath("$[0].origen").value("CLIENTE"));
    }

    @Test
    void elComplejoCreaEditaYBorraSusResenas() throws Exception {
        long id = canchaNueva();
        String json = mvc.perform(post("/api/admin/canchas/" + id + "/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Diego R.", 4, "Buena cancha para la pichanga de los jueves.", "2026-10-02")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("COMPLEJO"))
                .andExpect(jsonPath("$.codigoReserva", nullValue()))
                .andExpect(jsonPath("$.estado").value("PUBLICADA"))
                .andExpect(jsonPath("$.fecha").value("2026-10-02T12:00:00"))
                .andReturn().getResponse().getContentAsString();
        long idResena = ((Number) JsonPath.read(json, "$.id")).longValue();

        mvc.perform(get("/api/catalogo/canchas/" + id + "/resenas"))
                .andExpect(jsonPath("$.contenido[0].origen").value("COMPLEJO"))
                .andExpect(jsonPath("$.contenido[0].verificada").value(false));
        mvc.perform(get("/api/catalogo/canchas/" + id))
                .andExpect(jsonPath("$.resumenResenas.total").value(1))
                .andExpect(jsonPath("$.resumenResenas.promedio").value(4.0));

        mvc.perform(put("/api/admin/resenas/" + idResena).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Diego Ramos", 5, "Volvimos y la cancha estaba impecable.", "2026-10-05")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autor").value("Diego Ramos"))
                .andExpect(jsonPath("$.puntuacion").value(5))
                .andExpect(jsonPath("$.fecha").value("2026-10-05T12:00:00"));

        mvc.perform(post("/api/admin/canchas/" + id + "/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Ana P.", 3, "Todo correcto, aunque llegamos tarde.", "2026-09-01")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/canchas/" + id + "/resenas").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].autor").value("Diego Ramos"));

        mvc.perform(delete("/api/admin/resenas/" + idResena).header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/canchas/" + id + "/resenas").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/admin/resenas/" + idResena).header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasResenasDeClientesNoSeEditanNiSeBorran() throws Exception {
        // id de una reseña de cliente de la semilla (cancha 1).
        String json = mvc.perform(get("/api/admin/canchas/1/resenas").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("CLIENTE"))
                .andReturn().getResponse().getContentAsString();
        long idCliente = ((Number) JsonPath.read(json, "$[0].id")).longValue();
        String mensaje = "Las reseñas de clientes no se editan ni se borran; puedes ocultarlas o responderlas.";
        mvc.perform(put("/api/admin/resenas/" + idCliente).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Otro Autor", 1, "Intento cambiar lo que dijo el cliente.", "2026-10-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESENA_DE_CLIENTE"))
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(delete("/api/admin/resenas/" + idCliente).header("Authorization", tokenAdmin()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESENA_DE_CLIENTE"));
    }

    @Test
    void validacionesYPermisosDeLasResenasDelComplejo() throws Exception {
        String manana = Tiempo.hoy().plusDays(1).toString();
        mvc.perform(post("/api/admin/canchas/2/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Diego R.", 5, "Excelente cancha, muy bien iluminada.", manana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La fecha de la reseña no puede ser futura."));
        mvc.perform(post("/api/admin/canchas/2/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"autor\": \"D3\", \"puntuacion\": 0, \"comentario\": \"www.x.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("autor", "puntuacion", "comentario", "fecha")));
        mvc.perform(post("/api/admin/canchas/99999/resenas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Diego R.", 5, "Excelente cancha, muy bien iluminada.", "2026-10-01")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/canchas/99999/resenas").header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/canchas/2/resenas").header("Authorization", tokenRecepcion()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/resenas/1").header("Authorization", tokenCliente("70123456")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/resenas/999999").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resenaComplejo("Diego R.", 5, "Excelente cancha, muy bien iluminada.", "2026-10-01")))
                .andExpect(status().isNotFound());
        verify(reservas, never()).elegibleResena(any(), any(), any());
    }
}
