package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.util.Horarios;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Horario propio de cada cancha (SPEC_V2 §A): semilla, guardado con validaciones, vista previa, evento e interno. */
class HorarioTest extends PruebaIntegracion {

    static final String INTERNO = "token-interno-solo-para-desarrollo-local";

    /** Lunes a viernes 08:00–cierreSemana; sábado y domingo 07:00–cierreFinde. */
    static String horario(int duracion, String cierreSemana, String cierreFinde, String modo, String recargo) {
        StringBuilder dias = new StringBuilder();
        for (int d = 1; d <= 7; d++) {
            boolean finde = d >= 6;
            dias.append(d > 1 ? "," : "").append("""
                    {"diaSemana": %d, "abierto": true, "apertura": "%s", "cierre": "%s"}"""
                    .formatted(d, finde ? "07:00" : "08:00", finde ? cierreFinde : cierreSemana));
        }
        return """
                {"duracionTurnoMin": %d, "inicioNoche": "18:00", "dias": [%s],
                 "feriado": {"modo": "%s", "recargo": %s}}
                """.formatted(duracion, dias, modo, recargo);
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
    void laSemillaDaHorarioALasCanchasExistentes() throws Exception {
        mvc.perform(get("/api/admin/canchas/1/horario").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionTurnoMin").value(60))
                .andExpect(jsonPath("$.inicioNoche").value("18:00"))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[0].diaSemana").value(1))
                .andExpect(jsonPath("$.dias[0].apertura").value("08:00"))
                .andExpect(jsonPath("$.dias[6].cierre").value("23:00"))
                .andExpect(jsonPath("$.texto").value("Todos los días 8:00 a. m.–11:00 p. m."))
                .andExpect(jsonPath("$.feriado.modo").value("ABIERTA"))
                .andExpect(jsonPath("$.feriado.recargo").value(10.00));
        mvc.perform(get("/api/catalogo/canchas/losa-multiusos"))
                .andExpect(jsonPath("$.horario.texto").value("Todos los días 8:00 a. m.–10:00 p. m."))
                .andExpect(jsonPath("$.horario.dias[0].cierre").value("22:00"))
                .andExpect(jsonPath("$.feriado.recargo").value(10.00));
    }

    @Test
    void unaCanchaNuevaAbreTodosLosDiasConElHorarioGlobal() throws Exception {
        long id = canchaNueva();
        mvc.perform(get("/api/admin/canchas/" + id).header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.horario.duracionTurnoMin").value(60))
                .andExpect(jsonPath("$.horario.dias", hasSize(7)))
                .andExpect(jsonPath("$.horario.texto").value("Todos los días 8:00 a. m.–11:00 p. m."))
                .andExpect(jsonPath("$.feriado.modo").value("ABIERTA"));
    }

    @Test
    void guardarElHorarioPublicaElEventoConLaSemanaYElInternoLaDevuelve() throws Exception {
        long id = canchaNueva();
        mvc.perform(put("/api/admin/canchas/" + id + "/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(60, "23:00", "24:00", "CERRADA", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–12:00 a. m."))
                .andExpect(jsonPath("$.dias[6].cierre").value("24:00"))
                .andExpect(jsonPath("$.feriado.modo").value("CERRADA"))
                .andExpect(jsonPath("$.feriado.recargo").value(0.0));

        ArgumentCaptor<EventoDominio> captor = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.CANCHA_ACTUALIZADA), captor.capture());
        CanchaActualizadaEvento e = (CanchaActualizadaEvento) captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(e.idCancha()).isEqualTo(id);
        assertThat(e.duracionTurnoMin()).isEqualTo(60);
        assertThat(e.inicioNoche()).isEqualTo(LocalTime.of(18, 0));
        assertThat(e.feriadoModo()).isEqualTo("CERRADA");
        assertThat(e.recargoFeriado()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(e.dias()).hasSize(7);
        assertThat(e.dias().get(6).cierre()).isEqualTo(Horarios.FIN_DEL_DIA);
        assertThat(e.dias().get(0).apertura()).isEqualTo(LocalTime.of(8, 0));

        mvc.perform(get("/interno/canchas/" + id).header("X-Interno-Token", INTERNO))
                .andExpect(jsonPath("$.duracionTurnoMin").value(60))
                .andExpect(jsonPath("$.feriadoModo").value("CERRADA"))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[6].cierre").value("23:59:59"));
        mvc.perform(get("/api/admin/canchas/" + id + "/horario/vista-previa").param("dia", "7")
                        .header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(17)))
                .andExpect(jsonPath("$[0].horaInicio").value("07:00"))
                .andExpect(jsonPath("$[0].franja").value("DIA"))
                .andExpect(jsonPath("$[0].precio").value(50.0))
                .andExpect(jsonPath("$[11].horaInicio").value("18:00"))
                .andExpect(jsonPath("$[11].franja").value("NOCHE"))
                .andExpect(jsonPath("$[11].precio").value(70.0))
                .andExpect(jsonPath("$[16].horaFin").value("24:00"));
    }

    @Test
    void siSobraTiempoSeExplicaCuantoYAQueHoraAjustar() throws Exception {
        String mensaje = "Con turnos de 2 h, de 8:00 a. m. a 11:00 p. m. sobra 1 hora: ajusta el cierre a 10:00 p. m. o a 12:00 a. m.";
        mvc.perform(put("/api/admin/canchas/2/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(120, "23:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores[0].campo").value("dias[0].cierre"))
                .andExpect(jsonPath("$.errores", hasSize(5))); // sábado y domingo (7:00 a 11:00 p. m.) sí calzan
        // La vista previa sin guardar avisa lo mismo y, con un cierre que calza, muestra los turnos de 2 h.
        mvc.perform(post("/api/admin/canchas/2/horario/vista-previa").param("dia", "1")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(120, "23:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        mvc.perform(post("/api/admin/canchas/2/horario/vista-previa").param("dia", "1")
                        .header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(120, "22:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].horaInicio").value("20:00"))
                .andExpect(jsonPath("$[6].horaFin").value("22:00"));
        // No se guardó nada.
        mvc.perform(get("/api/admin/canchas/2/horario").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.duracionTurnoMin").value(60));
    }

    @Test
    void camposInvalidosDan400PorCampo() throws Exception {
        String seisDias = horario(60, "23:00", "23:00", "ABIERTA", "10").replace(
                ",{\"diaSemana\": 7, \"abierto\": true, \"apertura\": \"07:00\", \"cierre\": \"23:00\"}", "");
        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(seisDias))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dias"));

        String repetido = horario(60, "23:00", "23:00", "ABIERTA", "10").replace("\"diaSemana\": 7", "\"diaSemana\": 6");
        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(repetido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dias"));

        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(50, "23:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("duracionTurnoMin"));

        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(horario(15, "25:00", "23:00", "ABIERTA", "-1").replace("\"18:00\"", "\"6 pm\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("duracionTurnoMin", "inicioNoche", "dias[0].cierre",
                        "feriado.recargo")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItems("Escribe la hora como HH:mm (por ejemplo 18:00).",
                        "El adicional va de S/ 0.00 a S/ 999.99.")));

        String cierreAntes = horario(60, "07:00", "23:00", "ABIERTA", "10");
        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cierreAntes))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cierre debe ser posterior a la apertura."));

        String sinHoras = horario(60, "23:00", "23:00", "ABIERTA", "10")
                .replace("{\"diaSemana\": 1, \"abierto\": true, \"apertura\": \"08:00\", \"cierre\": \"23:00\"}",
                        "{\"diaSemana\": 1, \"abierto\": true}");
        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(sinHoras))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("dias[0].apertura", "dias[0].cierre")));

        String noEntra = horario(240, "23:00", "23:00", "ABIERTA", "10")
                .replace("\"apertura\": \"08:00\", \"cierre\": \"23:00\"", "\"apertura\": \"21:00\", \"cierre\": \"23:00\"");
        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(noEntra))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Entre la apertura y el cierre no entra ni un turno de 4 h."));

        mvc.perform(put("/api/admin/canchas/3/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dias\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("duracionTurnoMin", "inicioNoche", "dias", "feriado")));
    }

    @Test
    void unDiaCerradoNoTieneTurnosYConservaSusHoras() throws Exception {
        long id = canchaNueva();
        String domingoCerrado = horario(90, "23:00", "23:30", "ABIERTA", "15.50")
                .replace("{\"diaSemana\": 7, \"abierto\": true", "{\"diaSemana\": 7, \"abierto\": false")
                .replace("\"apertura\": \"08:00\", \"cierre\": \"23:00\"", "\"apertura\": \"08:00\", \"cierre\": \"21:30\"");
        mvc.perform(put("/api/admin/canchas/" + id + "/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(domingoCerrado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[6].abierto").value(false))
                .andExpect(jsonPath("$.dias[6].apertura").value("07:00"))
                .andExpect(jsonPath("$.texto").value("Lun a vie 8:00 a. m.–9:30 p. m. · Sáb 7:00 a. m.–11:30 p. m. · Dom cerrado"))
                .andExpect(jsonPath("$.feriado.recargo").value(15.5));
        mvc.perform(get("/api/admin/canchas/" + id + "/horario/vista-previa").param("dia", "7")
                        .header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/canchas/" + id + "/horario/vista-previa").param("dia", "1")
                        .header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[1].horaInicio").value("09:30"));
    }

    @Test
    void diaFueraDeRangoYCanchaInexistente() throws Exception {
        mvc.perform(get("/api/admin/canchas/1/horario/vista-previa").param("dia", "9").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dia"));
        mvc.perform(get("/api/admin/canchas/99999/horario").header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/canchas/99999/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(horario(60, "23:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloElAdministradorVeYCambiaElHorario() throws Exception {
        mvc.perform(get("/api/admin/canchas/1/horario")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/canchas/1/horario").header("Authorization", tokenRecepcion()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/canchas/1/horario").header("Authorization", tokenCliente("70123456"))
                        .contentType(MediaType.APPLICATION_JSON).content(horario(60, "23:00", "23:00", "ABIERTA", "10")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/canchas/1/horario").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.dias[0].apertura").value("08:00"));
    }
}
