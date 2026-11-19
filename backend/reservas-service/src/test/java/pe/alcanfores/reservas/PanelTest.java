package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.reservas.cliente.PagosCliente;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Panel: reserva manual con y sin pago inicial, asistencia, agenda, bloqueos, dashboard, listado y clientes. */
class PanelTest extends PruebaIntegracion {

    @Autowired
    private PagoEventosServicio pagoEventos;

    private static String manual(long idTurno, String pagoInicial) {
        return """
                {"idTurno": %d, "cliente": {"dni": "45678912", "nombres": "marco", "apellidos": "soto  ruiz",
                 "telefono": "987111222", "correo": "marco@example.com"}, "origen": "PRESENCIAL",
                 "nota": "Viene con su equipo", "pagoInicial": %s}
                """.formatted(idTurno, pagoInicial);
    }

    @Test
    void reservaManualConPagoEnCajaLlamaAPagosYNoTienePlazo() throws Exception {
        Turno t = turno(1, HOY, 11);
        String pago = "{\"tipoPago\":\"TOTAL\",\"metodo\":\"EFECTIVO\",\"monto\":60.00,\"comprobante\":{\"tipo\":\"BOLETA\"}}";
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content(manual(t.getId(), pago)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.venceEn").doesNotExist())
                .andExpect(jsonPath("$.pagoRegistrado").value(true))
                .andExpect(jsonPath("$.aviso").doesNotExist())
                .andExpect(jsonPath("$.cliente.apellidos").value("Soto Ruiz"));
        org.mockito.ArgumentCaptor<PagosCliente.PagoCaja> cobro = org.mockito.ArgumentCaptor.forClass(PagosCliente.PagoCaja.class);
        verify(pagos).registrarPagoCaja(cobro.capture());
        assertThat(cobro.getValue().tipoPago()).isEqualTo("TOTAL");
        assertThat(cobro.getValue().monto()).isEqualByComparingTo("60.00");
        assertThat(cobro.getValue().comprobante().tipo()).isEqualTo("BOLETA");
        assertThat(cobro.getValue().registradoPor()).isEqualTo(2L);
        Reserva r = reservas.findAll().get(0);
        assertThat(r.getRegistradoPor()).isEqualTo(2L);
        assertThat(r.getNota()).isEqualTo("Viene con su equipo");
    }

    @Test
    void siPagosNoRespondeLaReservaQuedaPendienteConAviso() throws Exception {
        doThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "El servicio de pagos no responde. Intenta en unos segundos."))
                .when(pagos).registrarPagoCaja(any());
        Turno t = turno(2, HOY.plusDays(1), 19);
        String pago = "{\"tipoPago\":\"ADELANTO\",\"metodo\":\"YAPE\",\"monto\":45.00,\"codigoOperacion\":\"12345678\","
                + "\"comprobante\":{\"tipo\":\"FACTURA\",\"ruc\":\"20000000001\",\"razonSocial\":\"Club Deportivo UTP S.A.C.\","
                + "\"direccionFiscal\":\"Av. Nicolás Ayllón 123, Ate\"}}";
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(manual(t.getId(), pago)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.pagoRegistrado").value(false))
                .andExpect(jsonPath("$.aviso").value(org.hamcrest.Matchers.containsString("quedó registrada como PENDIENTE")));
        assertThat(reservas.findAll().get(0).getVenceEn()).isNull();
    }

    @Test
    void validaElPagoInicialDeLaReservaManual() throws Exception {
        Turno t = turno(1, HOY.plusDays(1), 19);
        String yapeSinOperacionYFacturaMala = "{\"tipoPago\":\"ADELANTO\",\"metodo\":\"YAPE\",\"monto\":40.00,"
                + "\"comprobante\":{\"tipo\":\"FACTURA\",\"ruc\":\"15123456782\",\"razonSocial\":\"AB\"}}";
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(manual(t.getId(), yapeSinOperacionYFacturaMala)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", org.hamcrest.Matchers.hasItems("pagoInicial.codigoOperacion",
                        "pagoInicial.comprobante.ruc", "pagoInicial.comprobante.razonSocial",
                        "pagoInicial.comprobante.direccionFiscal")));
        String totalIncorrecto = "{\"tipoPago\":\"TOTAL\",\"metodo\":\"EFECTIVO\",\"monto\":50.00,\"comprobante\":{\"tipo\":\"BOLETA\"}}";
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(manual(t.getId(), totalIncorrecto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El pago total debe ser de S/ 80.00."));
        String montoMalo = "{\"tipoPago\":\"PARCIAL\",\"metodo\":\"BILLETE\",\"monto\":-3.555,\"comprobante\":{\"tipo\":\"NOTA\"}}";
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(manual(t.getId(), montoMalo).replace("PRESENCIAL", "WEB")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", org.hamcrest.Matchers.hasItems("origen", "pagoInicial.tipoPago",
                        "pagoInicial.metodo", "pagoInicial.monto", "pagoInicial.comprobante.tipo")));
        assertThat(reservas.count()).isZero();
        verify(pagos, never()).registrarPagoCaja(any());
    }

    @Test
    void asistenciaSoloParaConfirmadasYDesdeQuinceMinutosAntes() throws Exception {
        Turno t = turno(1, HOY, 12);
        String codigo = reservarWeb(t.getId(), "70123321").get("codigoReserva").asText();
        mvc.perform(post("/api/admin/reservas/" + codigo + "/asistencia").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asistencia\":\"ASISTIO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ASISTENCIA_NO_PERMITIDA"));
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("30.00"), "ADELANTO", "mtorres"));
        mvc.perform(post("/api/admin/reservas/" + codigo + "/asistencia").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asistencia\":\"ASISTIO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ASISTENCIA_ANTICIPADA"));
        fijarReloj(HOY.atTime(11, 50));
        mvc.perform(post("/api/admin/reservas/" + codigo + "/asistencia").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asistencia\":\"QUIZAS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Indica si el cliente asistió o no."));
        mvc.perform(post("/api/admin/reservas/" + codigo + "/asistencia").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"asistencia\":\"ASISTIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistencia").value("ASISTIO"))
                .andExpect(jsonPath("$.puedeCancelar").value(false));
        verify(publicador).publicar(eq(Eventos.RESERVA_ASISTENCIA), any(AsistenciaRegistradaEvento.class));
        mvc.perform(get("/interno/reservas/" + codigo + "/elegible-resena").header("X-Interno-Token", TOKEN_INTERNO)
                        .param("dni", "70123321").param("idCancha", "1"))
                .andExpect(jsonPath("$.elegible").value(true))
                .andExpect(jsonPath("$.autor").value("Diego R."));
        mvc.perform(get("/interno/reservas/" + codigo + "/elegible-resena").header("X-Interno-Token", TOKEN_INTERNO)
                        .param("dni", "70123321").param("idCancha", "2"))
                .andExpect(jsonPath("$.elegible").value(false));
    }

    @Test
    void agendaBloqueosYGeneracionIdempotente() throws Exception {
        Turno t = turno(3, HOY.plusDays(1), 8);
        String admin = token("ADMIN");
        mvc.perform(post("/api/admin/turnos/" + t.getId() + "/bloquear").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Mantenimiento del grass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.motivoBloqueo").value("Mantenimiento del grass"));
        mvc.perform(post("/api/admin/turnos/" + t.getId() + "/bloquear").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/turnos/" + t.getId() + "/desbloquear").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        mvc.perform(post("/api/admin/turnos/" + t.getId() + "/desbloquear").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());

        Turno reservado = turno(3, HOY.plusDays(1), 10);
        reservarWeb(reservado.getId(), "70123321");
        mvc.perform(post("/api/admin/turnos/" + reservado.getId() + "/bloquear").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Evento privado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_CON_RESERVA"));
        mvc.perform(post("/api/admin/turnos/bloquear-rango").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCancha\":3,\"fecha\":\"" + HOY.plusDays(1) + "\",\"horaDesde\":\"08:00\",\"horaHasta\":\"12:00\","
                                + "\"motivo\":\"Campeonato interno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueados").value(3))
                .andExpect(jsonPath("$.omitidos").value(1))
                .andExpect(jsonPath("$.horasOmitidas[0]").value("10:00"));
        mvc.perform(post("/api/admin/turnos/bloquear-rango").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCancha\":3,\"fecha\":\"" + HOY.plusDays(1) + "\",\"horaDesde\":\"12:00\",\"horaHasta\":\"10:00\","
                                + "\"motivo\":\"Campeonato interno\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("horaHasta"));

        mvc.perform(get("/api/admin/turnos").param("fecha", HOY.plusDays(1).toString()).param("idCancha", "3")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canchas.length()").value(1))
                .andExpect(jsonPath("$.canchas[0].turnos[2].reserva.codigoReserva").exists())
                .andExpect(jsonPath("$.canchas[0].turnos[2].reserva.cliente").value("Diego Armando Ramos Quispe"));

        String generar = "{\"desde\":\"" + HOY + "\",\"hasta\":\"" + HOY.plusDays(2) + "\"}";
        mvc.perform(post("/api/admin/turnos/generar").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(generar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(120))
                .andExpect(jsonPath("$.existentes").value(60));
        mvc.perform(post("/api/admin/turnos/generar").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(generar))
                .andExpect(jsonPath("$.creados").value(0))
                .andExpect(jsonPath("$.existentes").value(180));
        mvc.perform(post("/api/admin/turnos/generar").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desde\":\"" + HOY.minusDays(1) + "\",\"hasta\":\"" + HOY + "\",\"idsCancha\":[9]}"))
                .andExpect(status().isBadRequest());
        assertThat(turnos.findById(t.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.BLOQUEADO);
    }

    @Test
    void dashboardListadoYClientesConMetricas() throws Exception {
        String porValidar = reservarWeb(turno(1, HOY, 19).getId(), "70123321").get("codigoReserva").asText();
        pagoEventos.alRegistrarPago(new PagoRegistradoEvento(UUID.randomUUID(), AHORA, 1L, porValidar, "YAPE", "COMPROBANTE",
                new BigDecimal("40.00"), "ADELANTO"));
        String confirmada = reservarWeb(turno(2, HOY, 12).getId(), "70123322").get("codigoReserva").asText();
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 2L, confirmada, "PLIN", "COMPROBANTE",
                new BigDecimal("35.00"), "ADELANTO", "mtorres"));
        reservarWeb(turno(1, HOY.plusDays(1), 20).getId(), "70123321");

        mvc.perform(get("/api/admin/dashboard").header("Authorization", token("RECEPCION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-11-20"))
                .andExpect(jsonPath("$.kpis.reservasHoy").value(2))
                .andExpect(jsonPath("$.kpis.porValidar").value(1))
                .andExpect(jsonPath("$.kpis.confirmadasHoy").value(1))
                .andExpect(jsonPath("$.kpis.ocupacionHoyPct").value(3.3))
                .andExpect(jsonPath("$.kpis.reservasSemana").value(3))
                .andExpect(jsonPath("$.proximas.length()").value(3))
                .andExpect(jsonPath("$.proximas[0].codigoReserva").value(confirmada))
                .andExpect(jsonPath("$.agenda.length()").value(4))
                .andExpect(jsonPath("$.agenda[0].turnos[11].codigoReserva").value(porValidar))
                .andExpect(jsonPath("$.alertas[?(@.tipo=='POR_VALIDAR')].codigoReserva").value(porValidar))
                .andExpect(jsonPath("$.alertas[?(@.tipo=='SALDO_PENDIENTE')].codigoReserva").value(confirmada));

        mvc.perform(get("/api/admin/reservas").header("Authorization", token("RECEPCION"))
                        .param("q", "70123321").param("sort", "fecha,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].codigoReserva").value(porValidar))
                .andExpect(jsonPath("$.conteos.PENDIENTE").value(2))
                .andExpect(jsonPath("$.conteos.EN_REVISION").value(1));
        mvc.perform(get("/api/admin/reservas").header("Authorization", token("RECEPCION")).param("estado", "CONFIRMADA"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteos.CONFIRMADA").value(1));
        mvc.perform(get("/api/admin/reservas").header("Authorization", token("RECEPCION")).param("sort", "clave,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/reservas").header("Authorization", token("RECEPCION")).param("estado", "RARO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/reservas/" + confirmada).header("Authorization", token("RECEPCION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.dni").value("70123322"))
                .andExpect(jsonPath("$.puedeCobrarSaldo").value(true))
                .andExpect(jsonPath("$.linea.length()").value(3));

        mvc.perform(get("/api/admin/clientes").header("Authorization", token("RECEPCION")).param("q", "ramos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        JsonNode cliente = json.readTree(mvc.perform(get("/api/admin/clientes/70123321").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(cliente.at("/cliente/totalReservas").asInt()).isEqualTo(2);
        assertThat(cliente.at("/cliente/ultimaReserva").asText()).isEqualTo(HOY.plusDays(1).toString());
        assertThat(cliente.get("reservas")).hasSize(2);
        mvc.perform(get("/api/admin/clientes/99999999").header("Authorization", token("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/clientes/12").header("Authorization", token("ADMIN"))).andExpect(status().isBadRequest());

        mvc.perform(get("/interno/reservas/" + confirmada).header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.saldo").value(35.00))
                .andExpect(jsonPath("$.inicio").value("2026-11-20T12:00:00"))
                .andExpect(jsonPath("$.cliente.dni").value("70123322"));
        mvc.perform(get("/interno/reservas/proximas").header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
