package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.dto.ReservaDtos.ReprogramacionClienteSolicitud;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.TipoBloqueo;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;
import pe.alcanfores.reservas.servicio.ReservaServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/reservas/{codigo}/reprogramar: el cliente (o Canchita) cambia el horario conservando lo pagado. Mismas reglas
 * que al crear para los turnos nuevos; hasta 2 horas antes y una sola vez; total con los precios vigentes; atómico.
 */
class ReprogramacionClienteTest extends PruebaIntegracion {

    static final String DNI = "70123321";
    static final LocalDate MANANA = HOY.plusDays(1);
    static final LocalDate PASADO = HOY.plusDays(2);

    @Autowired
    private PagoEventosServicio pagoEventos;
    @Autowired
    private ReservaServicio servicio;

    static String cuerpo(String dni, Long... ids) {
        String lista = String.join(", ", java.util.Arrays.stream(ids).map(String::valueOf).toList());
        return "{\"dni\": \"" + dni + "\", \"idsTurno\": [" + lista + "]}";
    }

    private static MockHttpServletRequestBuilder pedido(String codigo, String cuerpo) {
        return post("/api/reservas/" + codigo + "/reprogramar").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private ResultActions reprogramar(String codigo, Long... ids) throws Exception {
        return mvc.perform(pedido(codigo, cuerpo(DNI, ids)));
    }

    private String reservar(Turno... ts) throws Exception {
        return reservarWebVarios(java.util.Arrays.stream(ts).map(Turno::getId).toList(), DNI).get("codigoReserva").asText();
    }

    /** Valida un pago (como lo haría pagos-service) por ese monto y tipo. */
    private void pagar(String codigo, String monto, String tipo) {
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal(monto), tipo, "mtorres"));
    }

    private Turno estado(Turno t) {
        return turnos.findById(t.getId()).orElseThrow();
    }

    private long cambiosGuardados() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reserva_reprogramacion", Long.class);
    }

    private ReservaReprogramadaEvento eventoPublicado() {
        ArgumentCaptor<EventoDominio> e = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_REPROGRAMADA), e.capture());
        return (ReservaReprogramadaEvento) e.getValue();
    }

    private AccionAuditadaEvento auditoria() {
        ArgumentCaptor<EventoDominio> e = ArgumentCaptor.forClass(EventoDominio.class);
        verify(publicador, org.mockito.Mockito.atLeastOnce()).publicar(eq(Eventos.AUDITORIA_ACCION), e.capture());
        return e.getAllValues().stream().map(AccionAuditadaEvento.class::cast)
                .filter(a -> "REPROGRAMAR_RESERVA".equals(a.accion())).findFirst().orElseThrow();
    }

    @Test
    void cambiaDeCanchaYDeDiaConservandoLoPagadoYSoloUnaVez() throws Exception {
        Turno viejo = turno(1, MANANA, 19);
        Turno nuevo = turno(2, PASADO, 20);
        String codigo = reservar(viejo);
        // Pagó S/ 50.00: cubre el adelanto del nuevo horario (S/ 45.00 de S/ 90.00).
        pagar(codigo, "50.00", "ADELANTO");
        mvc.perform(get("/api/reservas/" + codigo).param("dni", DNI))
                .andExpect(jsonPath("$.reprogramaciones").value(0))
                .andExpect(jsonPath("$.puedeReprogramar").value(true));

        reprogramar(codigo, nuevo.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoReserva").value(codigo))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("ADELANTO_PAGADO"))
                .andExpect(jsonPath("$.cancha.id").value(2))
                .andExpect(jsonPath("$.fecha").value(PASADO.toString()))
                .andExpect(jsonPath("$.horaInicio").value("20:00"))
                .andExpect(jsonPath("$.horaFin").value("21:00"))
                .andExpect(jsonPath("$.turnos[0].idTurno").value(nuevo.getId()))
                .andExpect(jsonPath("$.turnos[0].precio").value(90.0))
                .andExpect(jsonPath("$.montoTotal").value(90.0))
                .andExpect(jsonPath("$.montoAdelanto").value(45.0))
                .andExpect(jsonPath("$.montoPagado").value(50.0))
                .andExpect(jsonPath("$.montoEnRevision").value(0.0))
                .andExpect(jsonPath("$.saldo").value(40.0))
                .andExpect(jsonPath("$.reprogramaciones").value(1))
                .andExpect(jsonPath("$.puedeReprogramar").value(false))
                .andExpect(jsonPath("$.linea[-1:].evento").value("REPROGRAMADA"))
                .andExpect(jsonPath("$.linea[-1:].detalle").value("Horario cambiado por el cliente: Cancha 1 · Fútbol 5 · "
                        + "21/11/2026 7:00–8:00 p. m. (S/ 80.00) → Cancha 2 · Fútbol 6 · 22/11/2026 8:00–9:00 p. m. (S/ 90.00)."));

        mvc.perform(get("/api/cliente/reservas/" + codigo).header("Authorization", tokenCliente(DNI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprogramaciones").value(1))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
        assertThat(estado(viejo).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(estado(nuevo).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(jdbc.queryForList("SELECT id_turno FROM reserva_turno", Long.class)).containsExactly(nuevo.getId());
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM reserva_reprogramacion");
        assertThat(fila).containsEntry("actor", "CLIENTE").containsEntry("usuario", "Cliente 7****321")
                .containsEntry("turnos_anteriores", String.valueOf(viejo.getId()))
                .containsEntry("turnos_nuevos", String.valueOf(nuevo.getId()))
                .containsEntry("horario_anterior", "7:00–8:00 p. m.").containsEntry("cancha_nueva", "Cancha 2 · Fútbol 6");
        assertThat((BigDecimal) fila.get("total_anterior")).isEqualByComparingTo("80.00");
        assertThat((BigDecimal) fila.get("total_nuevo")).isEqualByComparingTo("90.00");

        ReservaReprogramadaEvento e = eventoPublicado();
        assertThat(e.reserva().codigoReserva()).isEqualTo(codigo);
        assertThat(e.reserva().nombreCancha()).isEqualTo("Cancha 2 · Fútbol 6");
        assertThat(e.reserva().fecha()).isEqualTo(PASADO);
        assertThat(e.reserva().horaInicio()).hasToString("20:00");
        assertThat(e.reserva().montoTotal()).isEqualByComparingTo("90.00");
        assertThat(e.reserva().montoPagado()).isEqualByComparingTo("50.00");
        assertThat(e.reserva().montoAdelanto()).isEqualByComparingTo("45.00");
        assertThat(e.reserva().cliente().telefono()).isEqualTo("987654321");
        assertThat(e.estado()).isEqualTo("CONFIRMADA");
        assertThat(e.confirmada()).isTrue();
        assertThat(e.estadoPago()).isEqualTo("ADELANTO_PAGADO");
        assertThat(e.venceEn()).isNull();
        assertThat(e.saldoPendiente()).isEqualByComparingTo("40.00");
        assertThat(e.canchaAnterior()).isEqualTo("Cancha 1 · Fútbol 5");
        assertThat(e.fechaAnterior()).isEqualTo(MANANA);
        assertThat(e.horaInicioAnterior()).hasToString("19:00");
        assertThat(e.horaFinAnterior()).hasToString("20:00");
        assertThat(e.horarioTextoAnterior()).isEqualTo("7:00–8:00 p. m.");
        assertThat(e.montoTotalAnterior()).isEqualByComparingTo("80.00");
        assertThat(e.actor()).isEqualTo("CLIENTE");
        assertThat(e.motivo()).isNull();
        AccionAuditadaEvento a = auditoria();
        assertThat(a.actor()).isEqualTo("Cliente 7****321");
        assertThat(a.rol()).isEqualTo("CLIENTE");
        assertThat(a.idEntidad()).isEqualTo(codigo);

        // El viejo turno quedó libre para otra persona; la segunda vez el cliente ya no puede.
        reservarWeb(viejo.getId(), "70123322");
        reprogramar(codigo, turno(3, PASADO, 20).getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMACION_NO_PERMITIDA"))
                .andExpect(jsonPath("$.mensaje").value("Ya cambiaste el horario una vez; escríbenos para ayudarte."));
        assertThat(cambiosGuardados()).isEqualTo(1);
    }

    @Test
    void correUnaHoraUnPartidoDeDosTurnosSobreSiMismo() throws Exception {
        Turno t19 = turno(1, MANANA, 19);
        Turno t20 = turno(1, MANANA, 20);
        Turno t21 = turno(1, MANANA, 21);
        String codigo = reservar(t19, t20);

        reprogramar(codigo, t21.getId(), t20.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("20:00"))
                .andExpect(jsonPath("$.horaFin").value("22:00"))
                .andExpect(jsonPath("$.horarioTexto").value("8:00–10:00 p. m."))
                .andExpect(jsonPath("$.cantidadTurnos").value(2))
                .andExpect(jsonPath("$.turnos[0].idTurno").value(t20.getId()))
                .andExpect(jsonPath("$.turnos[1].idTurno").value(t21.getId()))
                .andExpect(jsonPath("$.montoTotal").value(160.0))
                .andExpect(jsonPath("$.estadoPago").value("SIN_PAGO"));

        assertThat(estado(t19).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        assertThat(estado(t20).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(estado(t21).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(jdbc.queryForList("SELECT id_turno FROM reserva_turno WHERE vigente = 1 ORDER BY id_turno", Long.class))
                .containsExactly(t20.getId(), t21.getId());
        assertThat(jdbc.queryForObject("SELECT id_turno FROM reserva", Long.class)).isEqualTo(t20.getId());
        mvc.perform(get("/api/disponibilidad").param("fecha", MANANA.toString()).param("idCancha", "1"))
                .andExpect(jsonPath("$.canchas[0].turnos[11].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.canchas[0].turnos[12].estado").value("OCUPADO"))
                .andExpect(jsonPath("$.canchas[0].turnos[13].estado").value("OCUPADO"));
    }

    @Test
    void siChocaConOtraReservaResponde409YNoCambiaNada() throws Exception {
        Turno mio = turno(1, MANANA, 19);
        Turno ajeno = turno(1, MANANA, 20);
        Turno libre = turno(1, MANANA, 21);
        String codigo = reservar(mio);
        reservarWeb(ajeno.getId(), "70123322");

        reprogramar(codigo, libre.getId(), ajeno.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_OCUPADO"));

        assertThat(estado(mio).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
        assertThat(estado(libre).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getTurno().getId()).isEqualTo(mio.getId());
        assertThat(r.getMontoTotal()).isEqualByComparingTo("80.00");
        assertThat(cambiosGuardados()).isZero();
        verify(publicador, never()).publicar(eq(Eventos.RESERVA_REPROGRAMADA), any());

        // Un turno bloqueado por el complejo tampoco se puede tomar.
        Turno bloqueado = estado(libre);
        bloqueado.bloquear(TipoBloqueo.MANUAL, "Mantenimiento del grass");
        turnos.save(bloqueado);
        reprogramar(codigo, libre.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_NO_DISPONIBLE"));
    }

    @Test
    void dosReservasQueQuierenElMismoTurnoALaVezSoloUnaLoTiene() throws Exception {
        Turno a = turno(2, MANANA, 18);
        Turno b = turno(2, MANANA, 22);
        Turno disputado = turno(2, MANANA, 20);
        String codigoA = reservarWebVarios(List.of(a.getId()), "71000001").get("codigoReserva").asText();
        String codigoB = reservarWebVarios(List.of(b.getId()), "71000002").get("codigoReserva").asText();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> resultados = new ArrayList<>();
        for (String[] r : new String[][]{{codigoA, "71000001"}, {codigoB, "71000002"}}) {
            resultados.add(pool.submit(() -> {
                largada.await();
                try {
                    return servicio.reprogramar(r[0], new ReprogramacionClienteSolicitud(r[1], List.of(disputado.getId())), null)
                            .codigoReserva();
                } catch (NegocioExcepcion e) {
                    return e.getStatus() + " " + e.getCodigo();
                }
            }));
        }
        largada.countDown();
        List<String> salidas = new ArrayList<>();
        for (Future<String> f : resultados) {
            salidas.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(salidas).filteredOn(s -> s.startsWith("ALC-")).hasSize(1);
        assertThat(salidas).filteredOn("409 TURNO_OCUPADO"::equals).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva_turno WHERE id_turno = ? AND vigente = 1", Long.class,
                disputado.getId())).isEqualTo(1);
        assertThat(cambiosGuardados()).isEqualTo(1);
        // El perdedor conserva su turno.
        String perdedor = salidas.get(0).startsWith("ALC-") ? codigoB : codigoA;
        Turno suyo = perdedor.equals(codigoA) ? a : b;
        assertThat(reservas.findByCodigoReserva(perdedor).orElseThrow().getTurno().getId()).isEqualTo(suyo.getId());
        assertThat(estado(suyo).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void elNuevoTotalNoPuedeSerMenorQueLoPagadoNiQueElComprobanteEnRevision() throws Exception {
        Turno caro = turno(3, MANANA, 19);
        String codigo = reservar(caro);
        pagar(codigo, "120.00", "TOTAL");
        reprogramar(codigo, turno(1, MANANA, 19).getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_MONTO_MENOR"))
                .andExpect(jsonPath("$.mensaje").value("El nuevo horario cuesta menos de lo que ya pagaste. "
                        + "Elige otro horario o acércate al complejo."));
        assertThat(estado(caro).getEstado()).isEqualTo(EstadoTurno.RESERVADO);

        // Con un comprobante en revisión (adelanto de S/ 60.00) tampoco puede bajar de ese monto.
        String enRevision = reservarWebVarios(List.of(turno(3, PASADO, 19).getId()), "70123322").get("codigoReserva").asText();
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 2L, enRevision, "YAPE", "COMPROBANTE",
                new BigDecimal("60.00"), "ADELANTO"));
        mvc.perform(pedido(enRevision, cuerpo("70123322", turno(4, PASADO, 10).getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_MONTO_MENOR"));
    }

    @Test
    void recalculaElEstadoDelPagoYElAdelanto() throws Exception {
        // Pagó el adelanto (S/ 60.00) de S/ 120.00 y pasa a un turno de S/ 60.00: queda pagado en su totalidad.
        String codigo = reservar(turno(3, MANANA, 19));
        pagar(codigo, "60.00", "ADELANTO");
        reprogramar(codigo, turno(1, MANANA, 10).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("PAGADO_TOTAL"))
                .andExpect(jsonPath("$.montoTotal").value(60.0))
                .andExpect(jsonPath("$.montoAdelanto").value(30.0))
                .andExpect(jsonPath("$.saldo").value(0.0));

        // Pagó el total (S/ 80.00) y pasa a uno de S/ 90.00: vuelve a «adelanto pagado» con S/ 10.00 de saldo.
        String otro = reservarWebVarios(List.of(turno(1, PASADO, 19).getId()), "70123322").get("codigoReserva").asText();
        pagar(otro, "80.00", "TOTAL");
        mvc.perform(pedido(otro, cuerpo("70123322", turno(2, PASADO, 19).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("ADELANTO_PAGADO"))
                .andExpect(jsonPath("$.saldo").value(10.0));

        // Sin pagos: sigue PENDIENTE/SIN_PAGO con el adelanto nuevo y el mismo plazo para pagar.
        JsonNode pendiente = reservarWebVarios(List.of(turno(1, HOY.plusDays(3), 19).getId()), "70123323");
        String venceEn = pendiente.get("venceEn").asText();
        mvc.perform(pedido(pendiente.get("codigoReserva").asText(), cuerpo("70123323", turno(3, HOY.plusDays(3), 19).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoPago").value("SIN_PAGO"))
                .andExpect(jsonPath("$.montoAdelanto").value(60.0))
                .andExpect(jsonPath("$.venceEn").value(venceEn));
    }

    @Test
    void elTotalUsaElPrecioVigenteConRecargoDeFeriado() throws Exception {
        LocalDate feriado = HOY.plusDays(4);
        when(catalogo.calendario(any(), any())).thenReturn(List.of(new DiaEspecial(1L, feriado, "FERIADO", "Feriado de prueba", null)));
        complejo.refrescarCalendario();
        when(catalogo.canchas()).thenReturn(List.of(
                new pe.alcanfores.reservas.cliente.CanchaCatalogo(1L, "Cancha 1 · Fútbol 5", "FUTBOL_5", new BigDecimal("60.00"),
                        new BigDecimal("80.00"), "ACTIVA", 60, null, null, "ABIERTA", new BigDecimal("15.00"))));
        complejo.refrescar();
        String codigo = reservar(turno(1, MANANA, 19));

        reprogramar(codigo, turno(1, feriado, 19).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoTotal").value(95.0))
                .andExpect(jsonPath("$.turnos[0].precio").value(95.0));
    }

    @Test
    void validaLosTurnosNuevosComoAlCrear() throws Exception {
        Turno mio = turno(1, MANANA, 19);
        String codigo = reservar(mio);
        Turno a = turno(1, PASADO, 9);
        Turno otraCancha = turno(2, PASADO, 10);
        Long[] nueve = IntStream.range(0, 9).mapToObj(i -> a.getId() + i).toArray(Long[]::new);
        Object[][] casos = {
                {new Long[]{}, "Elige entre 1 y 8 turnos."},
                {nueve, "Elige entre 1 y 8 turnos."},
                {new Long[]{a.getId(), a.getId()}, "No repitas el mismo turno."},
                {new Long[]{a.getId(), otraCancha.getId()}, "Los turnos deben ser de la misma cancha y del mismo día."},
                {new Long[]{a.getId(), mio.getId()}, "Los turnos deben ser de la misma cancha y del mismo día."},
                {new Long[]{999_999L}, "Ese turno ya no está disponible."},
                {new Long[]{turno(1, HOY, 11).getId()}, "Ese turno ya no está disponible."},
                {new Long[]{turno(1, HOY.plusDays(15), 19).getId()}, "Elige una fecha entre hoy y los próximos 14 días."}};
        for (Object[] c : casos) {
            reprogramar(codigo, (Long[]) c[0])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("idsTurno"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(c[1]));
        }
        mvc.perform(pedido(codigo, "{\"dni\": \"70123321\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("idsTurno"));
        reprogramar(codigo, mio.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_MISMO_HORARIO"));
        // Código + DNI que no calzan (mismo 404 que la consulta), código y DNI mal escritos.
        mvc.perform(pedido(codigo, cuerpo("70123322", a.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No encontramos una reserva con ese código y DNI. Revisa ambos datos."));
        mvc.perform(pedido("ALC-XXXXXX", cuerpo(DNI, a.getId()))).andExpect(status().isNotFound());
        mvc.perform(pedido("ALC-123", cuerpo(DNI, a.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"));
        mvc.perform(pedido(codigo, cuerpo("7012", a.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dni"));
        assertThat(cambiosGuardados()).isZero();
        assertThat(estado(mio).getEstado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    @Test
    void soloHastaDosHorasAntesYNuncaUnaReservaCanceladaOJugada() throws Exception {
        // Hoy a las 12:00 (son las 10:40): faltan menos de 2 horas.
        Turno pronto = turno(1, HOY, 12);
        String codigo = reservar(pronto);
        mvc.perform(get("/api/reservas/" + codigo).param("dni", DNI)).andExpect(jsonPath("$.puedeReprogramar").value(false));
        reprogramar(codigo, turno(1, MANANA, 19).getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMACION_NO_PERMITIDA"))
                .andExpect(jsonPath("$.mensaje").value("Los cambios de horario se hacen hasta 2 horas antes del partido; "
                        + "escríbenos para ayudarte."));

        String cancelada = reservarWebVarios(List.of(turno(2, MANANA, 19).getId()), "70123322").get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + cancelada + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123322\",\"motivo\":\"Cambio de planes del equipo\"}")).andExpect(status().isOk());
        mvc.perform(pedido(cancelada, cuerpo("70123322", turno(2, MANANA, 20).getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_REPROGRAMABLE"))
                .andExpect(jsonPath("$.mensaje").value("Esta reserva está cancelada; ya no se puede cambiar el horario."));
        mvc.perform(get("/api/reservas/" + cancelada).param("dni", "70123322"))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
    }

    @Test
    void canchitaConTokenInternoQuedaComoActorYCuentaParaElLimite() throws Exception {
        String codigo = reservar(turno(1, MANANA, 19));
        mvc.perform(pedido(codigo, cuerpo(DNI, turno(1, MANANA, 20).getId()))
                        .header("X-Interno-Token", TOKEN_INTERNO).header("X-Canchita-Conversacion", "c12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linea[-1:].detalle").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.startsWith("Horario cambiado por Canchita (asistente): "))));
        assertThat(jdbc.queryForMap("SELECT actor, usuario FROM reserva_reprogramacion"))
                .containsEntry("actor", "CANCHITA").containsEntry("usuario", "Canchita (IA)");
        assertThat(eventoPublicado().actor()).isEqualTo("CANCHITA");
        AccionAuditadaEvento a = auditoria();
        assertThat(a.actor()).isEqualTo("Canchita (IA)");
        assertThat(a.rol()).isEqualTo("SISTEMA");
        assertThat(a.detalle()).endsWith(" · conversación c12");

        // Lo que hizo Canchita cuenta como el único cambio del cliente.
        reprogramar(codigo, turno(1, MANANA, 21).getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMACION_NO_PERMITIDA"));
    }

    @Test
    void unTokenInternoInvalidoSeIgnoraYElActorEsElCliente() throws Exception {
        String codigo = reservar(turno(1, MANANA, 19));
        mvc.perform(pedido(codigo, cuerpo(DNI, turno(1, MANANA, 20).getId()))
                        .header("X-Interno-Token", "token-falso").header("X-Canchita-Conversacion", "c12"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT actor FROM reserva_reprogramacion", String.class)).isEqualTo("CLIENTE");
        assertThat(auditoria().actor()).isEqualTo("Cliente 7****321");

        // Token válido pero conversación con caracteres raros: tampoco cuenta como Canchita.
        String otro = reservarWebVarios(List.of(turno(2, MANANA, 19).getId()), "70123322").get("codigoReserva").asText();
        mvc.perform(pedido(otro, cuerpo("70123322", turno(2, MANANA, 20).getId()))
                        .header("X-Interno-Token", TOKEN_INTERNO).header("X-Canchita-Conversacion", "c12; DROP"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT actor FROM reserva_reprogramacion", String.class)).containsOnly("CLIENTE");
    }

    private ResultActions reprogramarPanel(String rol, String codigo, String motivo, Long... ids) throws Exception {
        String lista = String.join(", ", java.util.Arrays.stream(ids).map(String::valueOf).toList());
        return mvc.perform(post("/api/admin/reservas/" + codigo + "/reprogramar").header("Authorization", token(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idsTurno\": [" + lista + "], \"motivo\": \"" + motivo + "\"}"));
    }

    @Test
    void conUnComprobanteEnRevisionCuentaSuMontoRealAunqueElAdelantoCambie() throws Exception {
        // Cancha 3 de noche (S/ 120.00, adelanto S/ 60.00): el cliente manda su Yape por S/ 60.00.
        String codigo = reservar(turno(3, MANANA, 19));
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 7L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("60.00"), "ADELANTO"));
        mvc.perform(get("/api/reservas/" + codigo).param("dni", DNI))
                .andExpect(jsonPath("$.estadoPago").value("EN_REVISION"))
                .andExpect(jsonPath("$.montoEnRevision").value(60.0));

        // Pasa a S/ 80.00 (adelanto S/ 40.00) y luego el panel a S/ 60.00: el adelanto baja, lo que se valida no.
        reprogramar(codigo, turno(1, MANANA, 19).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoAdelanto").value(40.0))
                .andExpect(jsonPath("$.montoEnRevision").value(60.0));
        reprogramarPanel("ADMIN", codigo, "Pidió jugar de día", turno(1, MANANA, 10).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoTotal").value(60.0))
                .andExpect(jsonPath("$.montoAdelanto").value(30.0));
        // Un turno de S/ 40.00 quedaría por debajo de los S/ 60.00 que se están validando (antes pasaba: 40 >= 30).
        reprogramarPanel("ADMIN", codigo, "Pidió la losa", turno(4, MANANA, 10).getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_MONTO_MENOR"));

        // Al validarse, lo pagado cubre el total y el monto en revisión vuelve a cero.
        pagar(codigo, "60.00", "ADELANTO");
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        assertThat(r.getMontoPagado()).isEqualByComparingTo("60.00");
        assertThat(r.getMontoEnRevision()).isEqualByComparingTo("0.00");
        assertThat(r.getEstadoPago()).isEqualTo(EstadoPago.PAGADO_TOTAL);
        assertThat(r.saldo()).isEqualByComparingTo("0.00");
    }

    @Test
    void siElComprobanteSeRechazaYaNoCuentaComoPagado() throws Exception {
        String codigo = reservar(turno(3, MANANA, 19));
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 8L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("60.00"), "ADELANTO"));
        pagoEventos.alRechazarPago(new PagoRechazadoEvento(UUID.randomUUID(), AHORA, 8L, codigo, "Voucher ilegible",
                "mtorres"));

        reprogramar(codigo, turno(4, MANANA, 10).getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("OBSERVADO"))
                .andExpect(jsonPath("$.montoTotal").value(40.0))
                .andExpect(jsonPath("$.montoEnRevision").value(0.0));
    }

    @Test
    void loPagadoDebeCubrirElAdelantoNuevoParaElClientePeroElPanelPuede() throws Exception {
        // Pagó S/ 40.00 de adelanto de un turno de S/ 80.00; dos turnos de noche de la cancha 3 piden S/ 120.00.
        Turno t19 = turno(3, MANANA, 19);
        Turno t20 = turno(3, MANANA, 20);
        String codigo = reservar(turno(1, MANANA, 19));
        pagar(codigo, "40.00", "ADELANTO");

        reprogramar(codigo, t19.getId(), t20.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_ADELANTO_INSUFICIENTE"))
                .andExpect(jsonPath("$.mensaje").value("El nuevo horario pide un adelanto mayor del que ya pagaste. "
                        + "Elige otro horario o escríbenos para ayudarte."));
        assertThat(cambiosGuardados()).isZero();
        assertThat(estado(t19).getEstado()).isEqualTo(EstadoTurno.DISPONIBLE);

        // Un comprobante en revisión menor que el adelanto nuevo tampoco alcanza.
        String enRevision = reservarWebVarios(List.of(turno(1, PASADO, 19).getId()), "70123322").get("codigoReserva").asText();
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 9L, enRevision, "YAPE", "COMPROBANTE",
                new BigDecimal("40.00"), "ADELANTO"));
        mvc.perform(pedido(enRevision, cuerpo("70123322", turno(3, PASADO, 19).getId(), turno(3, PASADO, 20).getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REPROGRAMAR_ADELANTO_INSUFICIENTE"));

        // El panel no tiene esa regla: queda confirmada con S/ 200.00 de saldo.
        reprogramarPanel("RECEPCION", codigo, "Campeonato del barrio", t19.getId(), t20.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.montoTotal").value(240.0))
                .andExpect(jsonPath("$.saldo").value(200.0));
    }

    @Test
    void unPagoTotalCalculadoAntesDeUnCambioMasCaroNoDejaLaReservaComoPagadaConSaldo() throws Exception {
        // Abrió un pago en línea «TOTAL» por S/ 80.00 y antes de pagarlo cambió a un turno de S/ 90.00.
        String codigo = reservar(turno(1, MANANA, 19));
        reprogramar(codigo, turno(2, MANANA, 19).getId()).andExpect(status().isOk());
        pagar(codigo, "80.00", "TOTAL");

        mvc.perform(get("/api/reservas/" + codigo).param("dni", DNI))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("ADELANTO_PAGADO"))
                .andExpect(jsonPath("$.saldo").value(10.0));
    }

    @Test
    void elAvisoLlevaElEstadoDelPagoYElPlazoParaPagar() throws Exception {
        JsonNode creada = reservarWebVarios(List.of(turno(1, MANANA, 19).getId()), DNI);
        reprogramar(creada.get("codigoReserva").asText(), turno(1, MANANA, 20).getId()).andExpect(status().isOk());

        ReservaReprogramadaEvento e = eventoPublicado();
        assertThat(e.estado()).isEqualTo("PENDIENTE");
        assertThat(e.estadoPago()).isEqualTo("SIN_PAGO");
        assertThat(e.venceEn()).isEqualTo(java.time.LocalDateTime.parse(creada.get("venceEn").asText()));
        assertThat(e.reserva().montoAdelanto()).isEqualByComparingTo("40.00");
    }
}
