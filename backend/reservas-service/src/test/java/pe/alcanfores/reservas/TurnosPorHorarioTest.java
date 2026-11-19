package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.HorarioDia;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.evento.ConsumidorEventos;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Franja;
import pe.alcanfores.reservas.modelo.TipoBloqueo;
import pe.alcanfores.reservas.modelo.Turno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v2 · C: turnos según el horario de cada cancha, regeneración, feriados, días no laborables y bloqueos por rango. */
class TurnosPorHorarioTest extends PruebaIntegracion {

    /** Sábado 21/11/2026 (el reloj está en el viernes 20). */
    static final LocalDate SABADO = HOY.plusDays(1);
    static final LocalDate MARTES = HOY.plusDays(4);

    @Autowired
    private ConsumidorEventos consumidor;

    private static List<HorarioDia> semana(LocalTime apertura, LocalTime cierre) {
        List<HorarioDia> dias = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            dias.add(new HorarioDia(d, true, apertura, cierre));
        }
        return dias;
    }

    private static CanchaCatalogo cancha(long id, String nombre, String precioDia, String precioNoche, Integer duracion,
                                         LocalTime noche, List<HorarioDia> dias, String feriado, String recargo) {
        return new CanchaCatalogo(id, nombre, "FUTBOL_5", new BigDecimal(precioDia), new BigDecimal(precioNoche), "ACTIVA",
                duracion, noche, dias, feriado, recargo == null ? null : new BigDecimal(recargo));
    }

    private List<Turno> deCancha(long idCancha, LocalDate fecha) {
        return turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha).stream()
                .filter(t -> t.getIdCancha() == idCancha).toList();
    }

    private Turno a(long idCancha, LocalDate fecha, LocalTime hora) {
        return deCancha(idCancha, fecha).stream().filter(t -> t.getHoraInicio().equals(hora)).findFirst().orElseThrow();
    }

    @Test
    void cadaCanchaGeneraSusTurnosSegunSuHorario() throws Exception {
        List<HorarioDia> sinSabado = new ArrayList<>(semana(LocalTime.of(8, 0), LocalTime.of(23, 0)));
        sinSabado.set(5, new HorarioDia(6, false, null, null));
        when(catalogo.canchas()).thenReturn(List.of(
                cancha(1, "Cancha 1", "60.00", "80.00", 90, LocalTime.of(19, 0), semana(LocalTime.of(8, 0), LocalTime.of(23, 0)), null, null),
                cancha(2, "Cancha 2", "70.00", "90.00", 60, null, sinSabado, null, null),
                cancha(4, "Losa", "40.00", "50.00", 60, null, semana(LocalTime.of(8, 0), Horarios.FIN_DEL_DIA), null, null)));
        complejo.refrescar();
        turnoServicio.generarRango(SABADO, SABADO, List.of());

        List<Turno> c1 = deCancha(1, SABADO);
        assertThat(c1).extracting(Turno::getHoraInicio).containsExactly(LocalTime.of(8, 0), LocalTime.of(9, 30),
                LocalTime.of(11, 0), LocalTime.of(12, 30), LocalTime.of(14, 0), LocalTime.of(15, 30), LocalTime.of(17, 0),
                LocalTime.of(18, 30), LocalTime.of(20, 0), LocalTime.of(21, 30));
        assertThat(a(1, SABADO, LocalTime.of(18, 30)).getFranja()).isEqualTo(Franja.DIA);
        assertThat(a(1, SABADO, LocalTime.of(20, 0)).getFranja()).isEqualTo(Franja.NOCHE);
        assertThat(a(1, SABADO, LocalTime.of(20, 0)).getPrecio()).isEqualByComparingTo("80.00");
        assertThat(deCancha(2, SABADO)).isEmpty();
        List<Turno> losa = deCancha(4, SABADO);
        assertThat(losa).hasSize(16);
        assertThat(losa.get(15).getHoraInicio()).isEqualTo(LocalTime.of(23, 0));
        assertThat(losa.get(15).getHoraFin()).isEqualTo(Horarios.FIN_DEL_DIA);
        // La cancha 3 no la mandó catálogo: sigue con la copia anterior (horario del complejo, 8:00 a 11:00 p. m.).
        assertThat(deCancha(3, SABADO)).hasSize(15);

        mvc.perform(get("/api/disponibilidad").param("fecha", SABADO.toString()).param("idCancha", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canchas[0].turnos[15].horaFin").value("23:59"))
                .andExpect(jsonPath("$.canchas[0].turnos[0].feriado").value(false))
                .andExpect(jsonPath("$.canchas[0].turnos[0].recargo").value(0.0));
        // Regenerar es idempotente.
        assertThat(turnoServicio.generarRango(SABADO, SABADO, List.of()).creados()).isZero();
    }

    @Test
    void alCambiarElHorarioSeRegeneraSinTocarReservasNiBloqueosManuales() throws Exception {
        Turno t19 = turno(1, MARTES, 19);
        String codigo = reservarWeb(t19.getId(), "70123321").path("codigoReserva").asText();
        Turno t9 = a(1, MARTES, LocalTime.of(9, 0));
        mvc.perform(post("/api/admin/turnos/" + t9.getId() + "/bloquear").header("Authorization", token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Mantenimiento\"}")).andExpect(status().isOk());
        // Reserva cancelada a las 13:00: ese turno no se puede borrar (la reserva lo referencia).
        Turno t13 = a(1, MARTES, LocalTime.of(13, 0));
        String cancelada = reservarWeb(t13.getId(), "70123322").path("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + cancelada + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123322\",\"motivo\":\"Cambiamos de día el partido\"}")).andExpect(status().isOk());

        consumidor.alActualizarCancha(new CanchaActualizadaEvento(UUID.randomUUID(), AHORA, 1L, "Cancha 1 · Fútbol 5",
                "FUTBOL_5", new BigDecimal("60.00"), new BigDecimal("80.00"), "ACTIVA", 90, LocalTime.of(18, 0),
                semana(LocalTime.of(8, 0), LocalTime.of(23, 0)), "ABIERTA", new BigDecimal("10.00")));

        List<Turno> visibles = deCancha(1, MARTES).stream().filter(Turno::visible).toList();
        assertThat(visibles).extracting(Turno::getHoraInicio).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(12, 30), LocalTime.of(14, 0), LocalTime.of(15, 30), LocalTime.of(17, 0), LocalTime.of(19, 0),
                LocalTime.of(20, 0), LocalTime.of(21, 30));
        Turno reservado = turnos.findById(t19.getId()).orElseThrow();
        assertThat(reservado.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(reservado.getHoraFin()).isEqualTo(LocalTime.of(20, 0));
        Turno manual = turnos.findById(t9.getId()).orElseThrow();
        assertThat(manual.getTipoBloqueo()).isEqualTo(TipoBloqueo.MANUAL);
        assertThat(a(1, MARTES, LocalTime.of(11, 0)).getHoraFin()).isEqualTo(LocalTime.of(12, 30));
        Turno oculto = turnos.findById(t13.getId()).orElseThrow();
        assertThat(oculto.getTipoBloqueo()).isEqualTo(TipoBloqueo.FUERA_DE_HORARIO);
        assertThat(oculto.visible()).isFalse();
        assertThat(reservas.findByCodigoReserva(codigo)).isPresent();

        mvc.perform(get("/api/disponibilidad").param("fecha", MARTES.toString()).param("idCancha", "1"))
                .andExpect(jsonPath("$.canchas[0].turnos.length()").value(9))
                .andExpect(jsonPath("$.canchas[0].turnos[*].idTurno", not(hasItem(t13.getId().intValue()))));
        mvc.perform(get("/api/admin/turnos").param("fecha", MARTES.toString()).param("idCancha", "1")
                        .header("Authorization", token("RECEPCION")))
                .andExpect(jsonPath("$.canchas[0].turnos.length()").value(9))
                .andExpect(jsonPath("$.canchas[0].turnos[0].tipoBloqueo").value("MANUAL"));
    }

    @Test
    void feriadoAbiertoCobraRecargoYCerradoBloquea() throws Exception {
        when(catalogo.canchas()).thenReturn(List.of(
                cancha(1, "Cancha 1", "60.00", "80.00", 60, null, null, "ABIERTA", "15.00"),
                cancha(2, "Cancha 2", "70.00", "90.00", 60, null, null, "CERRADA", null)));
        when(catalogo.calendario(any(), any())).thenReturn(List.of(
                new DiaEspecial(1L, MARTES, "FERIADO", "Inmaculada Concepción", null)));
        complejo.refrescar();
        assertThat(complejo.refrescarCalendario()).containsExactly(MARTES);
        turnoServicio.generarRango(MARTES, MARTES, List.of());

        Turno noche = a(1, MARTES, LocalTime.of(19, 0));
        assertThat(noche.getPrecio()).isEqualByComparingTo("95.00");
        assertThat(noche.getRecargo()).isEqualByComparingTo("15.00");
        assertThat(noche.isFeriado()).isTrue();
        assertThat(deCancha(2, MARTES)).allSatisfy(t -> {
            assertThat(t.getEstado()).isEqualTo(EstadoTurno.BLOQUEADO);
            assertThat(t.getTipoBloqueo()).isEqualTo(TipoBloqueo.FERIADO);
            assertThat(t.getMotivoBloqueo()).isEqualTo("Feriado: Inmaculada Concepción");
        });
        Turno dia = a(1, MARTES, LocalTime.of(10, 0));
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReservaVarios(List.of(dia.getId(), noche.getId()), "70123321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoTotal").value(170.0))
                .andExpect(jsonPath("$.montoAdelanto").value(85.0));
        mvc.perform(get("/api/disponibilidad").param("fecha", MARTES.toString()))
                .andExpect(jsonPath("$.canchas[0].turnos[0].feriado").value(true))
                .andExpect(jsonPath("$.canchas[0].turnos[0].recargo").value(15.0))
                .andExpect(jsonPath("$.canchas[1].turnos[0].estado").value("BLOQUEADO"));
        // El panel no puede desbloquear a mano un turno cerrado por el calendario.
        mvc.perform(post("/api/admin/turnos/" + a(2, MARTES, LocalTime.of(10, 0)).getId() + "/desbloquear")
                        .header("Authorization", token("ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("BLOQUEO_DE_CALENDARIO"));
    }

    @Test
    void unDiaNoLaborableBloqueaLasCanchasIndicadasYAlBorrarseLasLibera() throws Exception {
        Turno reservado = turno(3, MARTES, 20);
        reservarWeb(reservado.getId(), "70123321");
        Turno manual = a(3, MARTES, LocalTime.of(8, 0));
        turnoServicio.bloquear(manual.getId(), "Pintura de líneas");

        UUID idEvento = UUID.randomUUID();
        CalendarioActualizadoEvento cierre = new CalendarioActualizadoEvento(idEvento, AHORA, MARTES, "NO_LABORABLE",
                "Campeonato de la UTP", List.of(3L), false);
        consumidor.alActualizarCalendario(cierre);

        Turno libre = a(3, MARTES, LocalTime.of(10, 0));
        assertThat(libre.getEstado()).isEqualTo(EstadoTurno.BLOQUEADO);
        assertThat(libre.getTipoBloqueo()).isEqualTo(TipoBloqueo.NO_LABORABLE);
        assertThat(libre.getMotivoBloqueo()).isEqualTo("Campeonato de la UTP");
        assertThat(turnos.findById(reservado.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(turnos.findById(manual.getId()).orElseThrow().getMotivoBloqueo()).isEqualTo("Pintura de líneas");
        assertThat(a(1, MARTES, LocalTime.of(10, 0)).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(complejo.diaEspecial(MARTES)).isPresent();

        consumidor.alActualizarCalendario(new CalendarioActualizadoEvento(UUID.randomUUID(), AHORA, MARTES, "NO_LABORABLE",
                "Campeonato de la UTP", List.of(3L), true));
        assertThat(a(3, MARTES, LocalTime.of(10, 0)).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(turnos.findById(manual.getId()).orElseThrow().getTipoBloqueo()).isEqualTo(TipoBloqueo.MANUAL);
        assertThat(complejo.diaEspecial(MARTES)).isEmpty();
        // El mismo evento repetido no se vuelve a aplicar.
        consumidor.alActualizarCalendario(cierre);
        assertThat(a(3, MARTES, LocalTime.of(10, 0)).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
    }

    @Test
    void bloquearYDesbloquearPorRangoDeVariasCanchasYDias() throws Exception {
        turnoServicio.generarRango(SABADO, SABADO.plusDays(1), List.of());
        Turno ocupado = a(2, SABADO, LocalTime.of(19, 0));
        String codigo = reservarWeb(ocupado.getId(), "70123321").path("codigoReserva").asText();
        String admin = token("RECEPCION");
        String cuerpo = "{\"idsCancha\":[1,2],\"fecha\":\"" + SABADO + "\",\"fechaHasta\":\"" + SABADO.plusDays(1)
                + "\",\"horaDesde\":\"18:30\",\"horaHasta\":\"20:00\",\"motivo\":\"Torneo relámpago\"}";
        mvc.perform(post("/api/admin/turnos/bloquear-rango").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueados").value(7))
                .andExpect(jsonPath("$.omitidosConReserva[0]").value(codigo));
        assertThat(a(1, SABADO.plusDays(1), LocalTime.of(18, 0)).getTipoBloqueo()).isEqualTo(TipoBloqueo.MANUAL);
        assertThat(a(1, SABADO, LocalTime.of(20, 0)).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);

        String quitar = "{\"idsCancha\":[1,2],\"fecha\":\"" + SABADO + "\",\"fechaHasta\":\"" + SABADO.plusDays(1)
                + "\",\"horaDesde\":\"00:00\",\"horaHasta\":\"24:00\"}";
        mvc.perform(post("/api/admin/turnos/desbloquear-rango").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(quitar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desbloqueados").value(7));
        assertThat(a(1, SABADO.plusDays(1), LocalTime.of(18, 0)).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(turnos.findById(ocupado.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void elBloqueoPorRangoValidaCanchasFechasYHoras() throws Exception {
        String admin = token("ADMIN");
        String base = "{\"idsCancha\":%s,\"fecha\":\"%s\",\"fechaHasta\":%s,\"horaDesde\":\"%s\",\"horaHasta\":\"%s\","
                + "\"motivo\":\"Torneo relámpago\"}";
        Object[][] casos = {
                {"[]", HOY, "null", "08:00", "10:00", "idsCancha"},
                {"[9]", HOY, "null", "08:00", "10:00", "idsCancha"},
                {"[1]", HOY.minusDays(1), "null", "08:00", "10:00", "fecha"},
                {"[1]", HOY.plusDays(2), "\"" + HOY.plusDays(1) + "\"", "08:00", "10:00", "fechaHasta"},
                {"[1]", HOY, "\"" + HOY.plusDays(31) + "\"", "08:00", "10:00", "fechaHasta"},
                {"[1]", HOY, "null", "10:00", "10:00", "horaHasta"}};
        for (Object[] c : casos) {
            mvc.perform(post("/api/admin/turnos/bloquear-rango").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content(base.formatted(c[0], c[1], c[2], c[3], c[4])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value(c[5]));
        }
        mvc.perform(post("/api/admin/turnos/bloquear-rango").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(base.formatted("[1]", HOY.plusDays(40), "null", "08:00", "10:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SIN_TURNOS"));
        mvc.perform(post("/api/admin/turnos/desbloquear-rango").header("Authorization", tokenCliente("70123321"))
                        .contentType(MediaType.APPLICATION_JSON).content(base.formatted("[1]", HOY, "null", "08:00", "10:00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void laDisponibilidadGeneraAlVueloUnaFechaSinTurnos() throws Exception {
        LocalDate dia = HOY.plusDays(10);
        assertThat(turnos.existsByFecha(dia)).isFalse();
        mvc.perform(get("/api/disponibilidad").param("fecha", dia.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canchas[0].turnos.length()").value(15));
        assertThat(turnos.existsByFecha(dia)).isTrue();
    }

    @Test
    void laSincronizacionConCatalogoAplicaHorariosYCalendarioNuevos() {
        turnoServicio.generarRango(HOY, HOY.plusDays(2), List.of());
        when(catalogo.configuracion()).thenReturn(java.util.Map.of("dias_generados", "20", "dias_visibles", "14"));
        when(catalogo.canchas()).thenReturn(List.of(
                cancha(1, "Cancha 1 · Fútbol 5", "60.00", "80.00", 120, null, semana(LocalTime.of(8, 0), LocalTime.of(22, 0)),
                        "ABIERTA", "10.00")));
        when(catalogo.calendario(any(), any())).thenReturn(List.of(
                new DiaEspecial(5L, HOY.plusDays(2), "NO_LABORABLE", "Fumigación", null)));
        turnoServicio.sincronizarCatalogo();

        assertThat(complejo.parametros().horizonte()).isEqualTo(20);
        assertThat(deCancha(1, HOY.plusDays(1))).extracting(Turno::getHoraInicio)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(14, 0),
                        LocalTime.of(16, 0), LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertThat(deCancha(1, HOY.plusDays(20))).hasSize(7);
        assertThat(deCancha(2, HOY.plusDays(2))).allSatisfy(t -> assertThat(t.getTipoBloqueo()).isEqualTo(TipoBloqueo.NO_LABORABLE));
        // Si catálogo no responde se conserva el calendario anterior.
        when(catalogo.calendario(any(), any())).thenThrow(new IllegalStateException("caído"));
        assertThat(complejo.refrescarCalendario()).isEmpty();
        assertThat(complejo.diaEspecial(HOY.plusDays(2))).isPresent();
    }

    @Test
    void laCanchaInactivaBloqueaYLaActivaLibera() {
        Turno t = turno(4, MARTES, 10);
        turnoServicio.aplicarCambioCancha(new CanchaCatalogo(4L, "Losa multiusos", "MULTIUSO", new BigDecimal("40.00"),
                new BigDecimal("50.00"), "INACTIVA"));
        assertThat(turnos.findById(t.getId()).orElseThrow().getTipoBloqueo()).isEqualTo(TipoBloqueo.CANCHA_INACTIVA);
        assertThat(turnoServicio.desbloquearRango(new pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoSolicitud(
                List.of(4L), null, MARTES, null, LocalTime.of(10, 0), LocalTime.of(11, 0))).desbloqueados()).isZero();
        turnoServicio.aplicarCambioCancha(CanchaCatalogo.semilla().get(3));
        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
    }
}
