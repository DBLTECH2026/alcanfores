package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import pe.alcanfores.catalogo.util.Slugs;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD de canchas del panel: validaciones por campo, duplicados, permisos y evento cancha.actualizada. */
class CanchaAdminTest extends PruebaIntegracion {

    static String cuerpo(String nombre, String precioDia, String precioNoche) {
        return """
                {"nombre": "%s", "deporte": "FUTBOL_5", "superficie": "Grass sintético", "capacidad": 10,
                 "descripcion": "Cancha de prueba techada con grass sintético.", "descripcionLarga": "Texto largo de prueba.",
                 "techada": true, "iluminacionLed": true, "medidas": "30 x 18 m",
                 "caracteristicas": ["Camerinos", "  Estacionamiento  ", "Camerinos"],
                 "precioDia": %s, "precioNoche": %s}
                """.formatted(nombre, precioDia, precioNoche);
    }

    static String nombreUnico() {
        return "Prueba " + UUID.randomUUID().toString().substring(0, 8);
    }

    long crear(String nombre) throws Exception {
        String json = mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, "50.00", "70.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    List<CanchaActualizadaEvento> eventosCancha() {
        ArgumentCaptor<EventoDominio> captor = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.CANCHA_ACTUALIZADA), captor.capture());
        return captor.getAllValues().stream().map(CanchaActualizadaEvento.class::cast).toList();
    }

    @Test
    void creaLaCanchaConSlugYPublicaElEventoTrasElCommit() throws Exception {
        String nombre = "Cancha Ñandú " + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, "55.5", "75")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(Slugs.de(nombre)))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.precioDia").value(55.50))
                .andExpect(jsonPath("$.caracteristicas.length()").value(2))
                .andExpect(jsonPath("$.caracteristicas", hasItems("Camerinos", "Estacionamiento")))
                .andExpect(jsonPath("$.resumenResenas.total").value(0));

        CanchaActualizadaEvento e = eventosCancha().get(0);
        assertThat(e.nombre()).isEqualTo(nombre);
        assertThat(e.precioDia()).isEqualByComparingTo(new BigDecimal("55.50"));
        assertThat(e.precioNoche()).isEqualByComparingTo("75.00");
        assertThat(e.estado()).isEqualTo("ACTIVA");
        assertThat(e.deporte()).isEqualTo("FUTBOL_5");
        assertThat(e.idEvento()).isNotNull();
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.AUDITORIA_ACCION), any());
    }

    @Test
    void datosInvalidosDan400ConErrorPorCampo() throws Exception {
        String malo = """
                {"nombre": "ab", "superficie": "", "capacidad": 50, "descripcion": "corta",
                 "techada": true, "iluminacionLed": null, "medidas": "grande",
                 "caracteristicas": [""], "precioDia": 5, "precioNoche": 1000.555}
                """;
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos marcados."))
                .andExpect(jsonPath("$.errores[*].campo", hasItems("nombre", "deporte", "superficie", "capacidad",
                        "descripcion", "iluminacionLed", "medidas", "caracteristicas[0]", "precioDia", "precioNoche")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("El nombre debe tener entre 3 y 60 caracteres.",
                        "La capacidad va de 2 a 30 jugadores.", "El precio debe estar entre S/ 10.00 y S/ 999.00.",
                        "Escribe las medidas como «30 x 18 m».")));
        verify(publicador, never()).publicar(eq(Eventos.CANCHA_ACTUALIZADA), any());
    }

    @Test
    void precioDeNocheMenorQueElDeDiaSeMarcaEnPrecioNoche() throws Exception {
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombreUnico(), "80.00", "60.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("precioNoche"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El precio de noche no puede ser menor que el de día."));
    }

    @Test
    void deporteDesconocidoDa400() throws Exception {
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(nombreUnico(), "50", "60").replace("FUTBOL_5", "TENIS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("deporte"));
    }

    @Test
    void nombreDuplicadoDa409SinImportarMayusculas() throws Exception {
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("cancha 1 · FÚTBOL 5", "50", "60")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CANCHA_DUPLICADA"));
        verify(publicador, never()).publicar(eq(Eventos.CANCHA_ACTUALIZADA), any());
    }

    @Test
    void sinTokenDa401YRecepcionDa403() throws Exception {
        mvc.perform(get("/api/admin/canchas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/admin/canchas").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token de sesión no es válido."));
        mvc.perform(post("/api/admin/canchas").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombreUnico(), "50", "60")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(put("/api/admin/canchas/1").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Cancha 1 · Fútbol 5", "60", "80")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/canchas/1/estado").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"INACTIVA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarCambiaPreciosSlugYPublicaElEvento() throws Exception {
        long id = crear(nombreUnico());
        String nuevoNombre = nombreUnico() + " Editada";
        mvc.perform(put("/api/admin/canchas/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nuevoNombre, "65.00", "85.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nuevoNombre))
                .andExpect(jsonPath("$.slug").value(Slugs.de(nuevoNombre)))
                .andExpect(jsonPath("$.precioNoche").value(85.00));
        List<CanchaActualizadaEvento> eventos = eventosCancha();
        CanchaActualizadaEvento ultimo = eventos.get(eventos.size() - 1);
        assertThat(ultimo.idCancha()).isEqualTo(id);
        assertThat(ultimo.precioNoche()).isEqualByComparingTo("85.00");
    }

    @Test
    void editarConElNombreDeOtraCanchaDa409YUnaInexistente404() throws Exception {
        long id = crear(nombreUnico());
        mvc.perform(put("/api/admin/canchas/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Losa multiusos", "50", "60")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/canchas/99999").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombreUnico(), "50", "60")))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactivarLaOcultaDelPortalPeroNoDeLosInternosYPublicaElEvento() throws Exception {
        long id = crear(nombreUnico());
        mvc.perform(patch("/api/admin/canchas/" + id + "/estado").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"INACTIVA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"));
        List<CanchaActualizadaEvento> eventos = eventosCancha();
        assertThat(eventos.get(eventos.size() - 1).estado()).isEqualTo("INACTIVA");

        mvc.perform(get("/api/catalogo/canchas/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/canchas").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
        mvc.perform(get("/interno/canchas/" + id).header("X-Interno-Token", "token-interno-solo-para-desarrollo-local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"));
    }

    @Test
    void estadoNuloOInvalidoDa400() throws Exception {
        mvc.perform(patch("/api/admin/canchas/1/estado").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige el estado: ACTIVA o INACTIVA."));
        mvc.perform(patch("/api/admin/canchas/1/estado").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CERRADA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mismoEstadoNoPublicaEvento() throws Exception {
        mvc.perform(patch("/api/admin/canchas/2/estado").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ACTIVA\"}"))
                .andExpect(status().isOk());
        verify(publicador, never()).publicar(eq(Eventos.CANCHA_ACTUALIZADA), any());
    }

    @Test
    void internosExigenElTokenDeServicio() throws Exception {
        mvc.perform(get("/interno/canchas")).andExpect(status().isForbidden());
        mvc.perform(get("/interno/canchas").header("Authorization", tokenAdmin())).andExpect(status().isForbidden());
        mvc.perform(get("/interno/canchas").header("X-Interno-Token", "token-interno-solo-para-desarrollo-local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Cancha 1 · Fútbol 5"))
                .andExpect(jsonPath("$[0].precioDia").value(60.00));
        mvc.perform(get("/interno/canchas/99999").header("X-Interno-Token", "token-interno-solo-para-desarrollo-local"))
                .andExpect(status().isNotFound());
    }
}
