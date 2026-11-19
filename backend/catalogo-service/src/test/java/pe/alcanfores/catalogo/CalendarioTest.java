package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Calendario de feriados y días no laborables (SPEC_V2 §B). Usa fechas de 2030 en adelante para no chocar con la semilla. */
class CalendarioTest extends PruebaIntegracion {

    static String dia(String fecha, String tipo, String nombre, String canchas) {
        return """
                {"fecha": "%s", "tipo": "%s", "nombre": "%s", "canchas": %s}
                """.formatted(fecha, tipo, nombre, canchas);
    }

    long crear(String cuerpo) throws Exception {
        String json = mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    List<CalendarioActualizadoEvento> eventos() {
        ArgumentCaptor<EventoDominio> captor = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.CALENDARIO_ACTUALIZADO), captor.capture());
        return captor.getAllValues().stream().map(CalendarioActualizadoEvento.class::cast).toList();
    }

    @Test
    void laSemillaTraeLosFeriadosDePeruDe2026Y2027() throws Exception {
        mvc.perform(get("/api/admin/calendario").param("anio", "2026").header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0].fecha").value("2026-01-01"))
                .andExpect(jsonPath("$[0].tipo").value("FERIADO"))
                .andExpect(jsonPath("$[0].canchas", nullValue()))
                .andExpect(jsonPath("$[*].nombre", hasItems("Jueves Santo", "Viernes Santo", "Batalla de Arica y Día de la Bandera",
                        "Día de la Fuerza Aérea del Perú", "Batalla de Ayacucho")))
                .andExpect(jsonPath("$[2].fecha").value("2026-04-03"));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2027-03-01").param("hasta", "2027-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fecha").value("2027-03-25"))
                .andExpect(jsonPath("$[1].nombre").value("Viernes Santo"));
        mvc.perform(get("/interno/calendario").param("desde", "2026-12-01").param("hasta", "2026-12-31")
                        .header("X-Interno-Token", HorarioTest.INTERNO))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/interno/calendario")).andExpect(status().isForbidden());
    }

    @Test
    void noLaborableEnCanchasLibresSeGuardaYAvisaAReservas() throws Exception {
        long id = crear(dia("2030-05-12", "NO_LABORABLE", "Mantenimiento del grass", "[3, 1, 3]"));
        verify(reservas).reservasVigentes(LocalDate.of(2030, 5, 12), List.of(1L, 3L));
        mvc.perform(get("/api/admin/calendario").param("anio", "2030").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].canchas[0]").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].nombre").value("Mantenimiento del grass"));
        CalendarioActualizadoEvento e = eventos().get(eventos().size() - 1);
        assertThat(e.fecha()).isEqualTo(LocalDate.of(2030, 5, 12));
        assertThat(e.tipo()).isEqualTo("NO_LABORABLE");
        assertThat(e.canchas()).containsExactly(1L, 3L);
        assertThat(e.eliminado()).isFalse();
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.AUDITORIA_ACCION), any());

        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-05-12", "FERIADO", "Otro nombre", "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DIA_ESPECIAL_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("Ese día ya está en el calendario como «Mantenimiento del grass»."))
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
    }

    @Test
    void conReservasEseDiaResponde409ConLosCodigos() throws Exception {
        when(reservas.reservasVigentes(eq(LocalDate.of(2030, 6, 2)), isNull())).thenReturn(List.of("ALC-AB2CD3", "ALC-EF4GH5"));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-06-02", "NO_LABORABLE", "Evento privado", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DIA_CON_RESERVAS"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Ese día ya hay reservas en esas canchas: ALC-AB2CD3, ALC-EF4GH5. Cancélalas o cámbialas antes."))
                .andExpect(jsonPath("$.errores[0].mensaje").value("ALC-AB2CD3, ALC-EF4GH5"));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2030-06-02").param("hasta", "2030-06-02"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void enFeriadoSoloCuentanLasCanchasQueCierran() throws Exception {
        String json = mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CanchaAdminTest.cuerpo(CanchaAdminTest.nombreUnico(), "50.00", "70.00")))
                .andReturn().getResponse().getContentAsString();
        long cerrada = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc.perform(put("/api/admin/canchas/" + cerrada + "/horario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(HorarioTest.horario(60, "23:00", "23:00", "CERRADA", "0")))
                .andExpect(status().isOk());
        when(reservas.reservasVigentes(eq(LocalDate.of(2030, 7, 1)), anyList())).thenReturn(List.of("ALC-JK6LM7"));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-07-01", "FERIADO", "Feriado regional", "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DIA_CON_RESERVAS"));
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass((Class) List.class);
        verify(reservas).reservasVigentes(eq(LocalDate.of(2030, 7, 1)), ids.capture());
        assertThat(ids.getValue()).contains(cerrada).doesNotContain(1L, 2L, 3L, 4L);
    }

    @Test
    void validacionesDelDiaEspecial() throws Exception {
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("fecha", "tipo", "nombre")));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-08-01", "NO_LABORABLE", "Visita www.x.com", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Escribe el nombre sin enlaces ni los signos < >."));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-08-01", "FERIADO", "Feriado local", "[1]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("canchas"));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-08-01", "NO_LABORABLE", "Pintado", "[1, 99999]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Alguna de las canchas elegidas no existe."));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-08-01", "VACACIONES", "Pintado", "null")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/calendario").param("anio", "1999").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("anio"));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2030-02-01").param("hasta", "2030-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2030-01-01").param("hasta", "2031-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Consulta como máximo 366 días."));
    }

    @Test
    void editarCambiandoLaFechaAvisaLaViejaYLaNuevaYBorrarLaLibera() throws Exception {
        long id = crear(dia("2030-09-10", "NO_LABORABLE", "Campeonato interno", "null"));
        clearInvocations(publicador);
        mvc.perform(put("/api/admin/calendario/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-09-11", "NO_LABORABLE", "Campeonato interno", "[2]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2030-09-11"))
                .andExpect(jsonPath("$.canchas[0]").value(2));
        List<CalendarioActualizadoEvento> e = eventos();
        assertThat(e).hasSize(2);
        assertThat(e.get(0).fecha()).isEqualTo(LocalDate.of(2030, 9, 10));
        assertThat(e.get(0).eliminado()).isTrue();
        assertThat(e.get(1).fecha()).isEqualTo(LocalDate.of(2030, 9, 11));
        assertThat(e.get(1).eliminado()).isFalse();

        long otro = crear(dia("2030-09-20", "FERIADO", "Feriado puente", "null"));
        mvc.perform(put("/api/admin/calendario/" + otro).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-09-11", "FERIADO", "Choque", "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DIA_ESPECIAL_DUPLICADO"));

        clearInvocations(publicador);
        mvc.perform(delete("/api/admin/calendario/" + id).header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());
        CalendarioActualizadoEvento borrado = eventos().get(0);
        assertThat(borrado.eliminado()).isTrue();
        assertThat(borrado.fecha()).isEqualTo(LocalDate.of(2030, 9, 11));
        mvc.perform(delete("/api/admin/calendario/" + id).header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/calendario/" + id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-09-12", "FERIADO", "No existe", "null")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cargarFeriadosDePeruNoDuplica() throws Exception {
        crear(dia("2028-07-28", "NO_LABORABLE", "Cerrado por inventario", "null"));
        mvc.perform(post("/api/admin/calendario/feriados-peru").param("anio", "2028").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(15))
                .andExpect(jsonPath("$.existentes").value(1));
        mvc.perform(post("/api/admin/calendario/feriados-peru").param("anio", "2028").header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.creados").value(0))
                .andExpect(jsonPath("$.existentes").value(16));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2028-04-13").param("hasta", "2028-04-14"))
                .andExpect(jsonPath("$[0].nombre").value("Jueves Santo"))
                .andExpect(jsonPath("$[1].nombre").value("Viernes Santo"));
        mvc.perform(get("/api/catalogo/calendario").param("desde", "2028-07-28").param("hasta", "2028-07-28"))
                .andExpect(jsonPath("$[0].tipo").value("NO_LABORABLE"));
        mvc.perform(post("/api/admin/calendario/feriados-peru").param("anio", "2101").header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lasFechasPasadasNoSeRevisanConReservasYReservasCaidoDa503() throws Exception {
        crear(dia("2020-03-16", "NO_LABORABLE", "Cierre por emergencia", "null"));
        verify(reservas, never()).reservasVigentes(eq(LocalDate.of(2020, 3, 16)), any());

        when(reservas.reservasVigentes(eq(LocalDate.of(2030, 10, 5)), any()))
                .thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                        "El servicio de reservas no responde. Intenta en unos segundos."));
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(dia("2030-10-05", "NO_LABORABLE", "Fumigación", "null")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void permisos() throws Exception {
        String cuerpo = dia("2030-11-11", "NO_LABORABLE", "Sin permiso", "null");
        mvc.perform(post("/api/admin/calendario").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/calendario").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/calendario").header("Authorization", tokenCliente("70123456")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/calendario/feriados-peru").header("Authorization", tokenRecepcion()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/calendario/1").header("Authorization", tokenRecepcion()))
                .andExpect(status().isForbidden());
    }
}
