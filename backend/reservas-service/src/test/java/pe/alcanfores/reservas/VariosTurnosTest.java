package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v2 · D: una reserva de VARIOS turnos de la misma cancha y fecha (seguidos o no). */
class VariosTurnosTest extends PruebaIntegracion {

    static final LocalDate DIA = HOY.plusDays(2);

    @Autowired
    private ExpiracionServicio expiracion;

    @Test
    void reservaDeDosTurnosNoSeguidosSumaElTotalYDescribeElHorario() throws Exception {
        Turno manana = turno(1, DIA, 9);
        Turno noche = turno(1, DIA, 19);
        JsonNode r = reservarWebVarios(List.of(noche.getId(), manana.getId()), "70123321");

        assertThat(r.get("montoTotal").decimalValue()).isEqualByComparingTo("140.00");
        assertThat(r.get("montoAdelanto").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(r.get("horarioTexto").asText()).isEqualTo("9:00–10:00 a. m. y 7:00–8:00 p. m.");
        assertThat(r.get("cantidadTurnos").asInt()).isEqualTo(2);
        assertThat(r.get("horaInicio").asText()).isEqualTo("09:00");
        assertThat(r.get("horaFin").asText()).isEqualTo("20:00");
        assertThat(r.get("turnos").get(0).get("idTurno").asLong()).isEqualTo(manana.getId());
        assertThat(r.get("turnos").get(1).get("precio").decimalValue()).isEqualByComparingTo("80.00");
        // El plazo para pagar se cuenta desde el registro, sin pasar del primer turno.
        assertThat(r.get("venceEn").asText()).startsWith("2026-11-20T11:00");

        assertThat(turnos.findAllById(List.of(manana.getId(), noche.getId())))
                .allSatisfy(t -> assertThat(t.getEstado()).isEqualTo(EstadoTurno.RESERVADO));
        ArgumentCaptor<EventoDominio> evento = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_REGISTRADA), evento.capture());
        DatosReserva datos = ((ReservaRegistradaEvento) evento.getValue()).reserva();
        assertThat(datos.horarioTexto()).isEqualTo("9:00–10:00 a. m. y 7:00–8:00 p. m.");
        assertThat(datos.cantidadTurnos()).isEqualTo(2);
        assertThat(datos.horaInicio()).hasToString("09:00");
        assertThat(datos.horaFin()).hasToString("20:00");

        String codigo = r.get("codigoReserva").asText();
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(jsonPath("$.turnos.length()").value(2))
                .andExpect(jsonPath("$.cantidadTurnos").value(2))
                .andExpect(jsonPath("$.resenada").value(false));
        mvc.perform(get("/api/disponibilidad").param("fecha", DIA.toString()).param("idCancha", "1"))
                .andExpect(jsonPath("$.canchas[0].turnos[1].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[11].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[2].estado").value("DISPONIBLE"));
    }

    @Test
    void dosTurnosSeguidosSonUnSoloTramoYLaAgendaLosMuestraEnCadaCelda() throws Exception {
        Turno t19 = turno(2, DIA, 19);
        Turno t20 = turno(2, DIA, 20);
        String codigo = reservarWebVarios(List.of(t19.getId(), t20.getId()), "70123321").path("codigoReserva").asText();
        String admin = token("RECEPCION");
        mvc.perform(get("/api/admin/turnos").param("fecha", DIA.toString()).param("idCancha", "2").header("Authorization", admin))
                .andExpect(jsonPath("$.canchas[0].turnos[11].reserva.codigoReserva").value(codigo))
                .andExpect(jsonPath("$.canchas[0].turnos[12].reserva.codigoReserva").value(codigo));
        mvc.perform(get("/api/admin/reservas/" + codigo).header("Authorization", admin))
                .andExpect(jsonPath("$.horarioTexto").value("7:00–9:00 p. m."))
                .andExpect(jsonPath("$.horaFin").value("21:00"))
                .andExpect(jsonPath("$.montoTotal").value(180.0));
        mvc.perform(get("/api/admin/reservas").param("q", codigo).header("Authorization", admin))
                .andExpect(jsonPath("$.contenido[0].cantidadTurnos").value(2))
                .andExpect(jsonPath("$.contenido[0].turnos.length()").value(2));
        mvc.perform(get("/api/admin/dashboard").param("fecha", DIA.toString()).header("Authorization", admin))
                .andExpect(jsonPath("$.agenda[1].turnos[11].codigoReserva").value(codigo))
                .andExpect(jsonPath("$.agenda[1].turnos[12].codigoReserva").value(codigo));
        mvc.perform(get("/interno/reservas/" + codigo).header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(jsonPath("$.horarioTexto").value("7:00–9:00 p. m."))
                .andExpect(jsonPath("$.cantidadTurnos").value(2))
                .andExpect(jsonPath("$.turnos[1].horaInicio").value("20:00"));
        mvc.perform(get("/api/admin/reservas/" + codigo + "/constancia.pdf").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void validaLaListaDeTurnos() throws Exception {
        Turno a = turno(1, DIA, 9);
        Turno b = turno(2, DIA, 10);
        Turno otroDia = turno(1, DIA.plusDays(1), 9);
        List<Long> nueve = IntStream.range(0, 9).mapToObj(i -> a.getId() + i).map(Long::valueOf).toList();
        Object[][] casos = {
                {nueve, "Elige entre 1 y 8 turnos."},
                {List.of(a.getId(), a.getId()), "No repitas el mismo turno."},
                {List.of(a.getId(), b.getId()), "Los turnos deben ser de la misma cancha y del mismo día."},
                {List.of(a.getId(), otroDia.getId()), "Los turnos deben ser de la misma cancha y del mismo día."},
                {List.of(a.getId(), 999_999L), "Ese turno ya no está disponible."}};
        for (Object[] c : casos) {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) c[0];
            mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReservaVarios(ids, "70123321")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("idsTurno"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(c[1]));
        }
        String sinTurnos = cuerpoReserva(1, "70123321").replace("\"idTurno\": 1,", "");
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(sinTurnos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("idsTurno"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige entre 1 y 8 turnos."));
        // Si uno de los turnos ya está tomado, toda la reserva se rechaza con 409.
        reservarWeb(a.getId(), "70123322");
        Turno libre = turno(1, DIA, 10);
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReservaVarios(List.of(libre.getId(), a.getId()), "70123321")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_OCUPADO"));
        assertThat(turnos.findById(libre.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(reservas.count()).isEqualTo(1);
    }

    @Test
    void cancelarYExpirarLiberanTodosLosTurnos() throws Exception {
        Turno t9 = turno(3, DIA, 9);
        Turno t11 = turno(3, DIA, 11);
        String codigo = reservarWebVarios(List.of(t9.getId(), t11.getId()), "70123321").path("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"Se suspendió el partido\"}"))
                .andExpect(status().isOk());
        assertThat(turnos.findAllById(List.of(t9.getId(), t11.getId())))
                .allSatisfy(t -> assertThat(t.getEstado()).isEqualTo(EstadoTurno.DISPONIBLE));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva_turno WHERE vigente = 1", Integer.class)).isZero();
        ArgumentCaptor<EventoDominio> evento = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, atLeastOnce()).publicar(eq(Eventos.RESERVA_CANCELADA), evento.capture());
        assertThat(((ReservaCanceladaEvento) evento.getValue()).reserva().cantidadTurnos()).isEqualTo(2);

        // Los mismos turnos se pueden volver a reservar; si nadie paga, la expiración también los libera a ambos.
        reservarWebVarios(List.of(t9.getId(), t11.getId()), "70123322");
        fijarReloj(AHORA.plusMinutes(25));
        assertThat(expiracion.expirarVencidas()).isEqualTo(1);
        assertThat(turnos.findAllById(List.of(t9.getId(), t11.getId())))
                .allSatisfy(t -> assertThat(t.getEstado()).isEqualTo(EstadoTurno.DISPONIBLE));
        reservarWebVarios(List.of(t11.getId()), "70123323");
    }

    @Test
    void laReservaManualAceptaVariosTurnos() throws Exception {
        Turno t17 = turno(4, DIA, 17);
        Turno t18 = turno(4, DIA, 18);
        String cuerpo = """
                {"idsTurno": [%d, %d], "cliente": {"dni": "70123321", "nombres": "Lucía", "apellidos": "Mendoza",
                 "telefono": "987654321", "correo": "lucia@example.com"}, "origen": "PRESENCIAL"}
                """.formatted(t17.getId(), t18.getId());
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadTurnos").value(2))
                .andExpect(jsonPath("$.montoTotal").value(90.0))
                .andExpect(jsonPath("$.horarioTexto").value("5:00–7:00 p. m."));
    }

    @Test
    void internoDiceQueReservasVigentesHayEnUnDia() throws Exception {
        Turno t = turno(1, DIA, 15);
        String codigo = reservarWeb(t.getId(), "70123321").path("codigoReserva").asText();
        mvc.perform(get("/interno/reservas/vigentes").header("X-Interno-Token", TOKEN_INTERNO).param("fecha", DIA.toString()))
                .andExpect(jsonPath("$.codigos[0]").value(codigo));
        mvc.perform(get("/interno/reservas/vigentes").header("X-Interno-Token", TOKEN_INTERNO).param("fecha", DIA.toString())
                        .param("idsCancha", "1", "3"))
                .andExpect(jsonPath("$.codigos.length()").value(1));
        mvc.perform(get("/interno/reservas/vigentes").header("X-Interno-Token", TOKEN_INTERNO).param("fecha", DIA.toString())
                        .param("idsCancha", "2"))
                .andExpect(jsonPath("$.codigos.length()").value(0));
        mvc.perform(get("/interno/reservas/vigentes").param("fecha", DIA.toString())).andExpect(status().isForbidden());
    }

    @Test
    void v4CopiaElTurnoDeLasReservasExistentesYMarcaLosBloqueos() throws Exception {
        Turno vigente = turno(1, DIA, 19);
        Turno cancelado = turno(1, DIA, 20);
        Long idCliente = 1L;
        jdbc.update("INSERT INTO cliente (id_cliente, dni, nombres, apellidos, telefono, correo, fecha_registro, actualizado_en) "
                + "VALUES (1, '70123321', 'Ana', 'Pérez', '987654321', 'ana@example.com', NOW(), NOW())");
        String insertar = "INSERT INTO reserva (codigo_reserva, id_cliente, id_turno, estado, estado_pago, origen, monto_total,"
                + " monto_adelanto, fecha_registro) VALUES (?, ?, ?, ?, 'SIN_PAGO', 'WEB', 80, 40, NOW())";
        jdbc.update(insertar, "ALC-AAAAAA", idCliente, vigente.getId(), "CONFIRMADA");
        jdbc.update(insertar, "ALC-BBBBBB", idCliente, cancelado.getId(), "CANCELADA");
        Long t9 = turno(2, DIA, 9).getId();
        Long t10 = turno(2, DIA, 10).getId();
        jdbc.update("UPDATE turno SET estado = 'BLOQUEADO', motivo_bloqueo = 'Cancha inactiva', tipo_bloqueo = NULL WHERE id_turno = ?", t9);
        jdbc.update("UPDATE turno SET estado = 'BLOQUEADO', motivo_bloqueo = 'Pintura', tipo_bloqueo = NULL WHERE id_turno = ?", t10);

        String sql = new ClassPathResource("db/migration/V4__turnos_por_horario_y_varios_turnos.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String sinComentarios = sql.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (x, y) -> x + "\n" + y);
        Arrays.stream(sinComentarios.split(";")).map(String::strip)
                .filter(s -> s.startsWith("UPDATE") || s.startsWith("INSERT"))
                .forEach(jdbc::update);

        assertThat(jdbc.queryForList("SELECT CONCAT(id_turno, ':', precio, ':', vigente) FROM reserva_turno ORDER BY id_turno",
                String.class)).containsExactly(vigente.getId() + ":80.00:1", cancelado.getId() + ":80.00:0");
        assertThat(jdbc.queryForList("SELECT tipo_bloqueo FROM turno WHERE estado = 'BLOQUEADO' ORDER BY hora_inicio", String.class))
                .containsExactly("CANCHA_INACTIVA", "MANUAL");
        mvc.perform(get("/api/reservas/ALC-AAAAAA").param("dni", "70123321"))
                .andExpect(jsonPath("$.cantidadTurnos").value(1))
                .andExpect(jsonPath("$.horarioTexto").value("7:00–8:00 p. m."));
    }
}
