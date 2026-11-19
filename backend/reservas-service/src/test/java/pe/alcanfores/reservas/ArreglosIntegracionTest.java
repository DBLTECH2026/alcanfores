package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.reservas.modelo.ConsultaDocumento;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.TipoDocumento;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ConsultaDocumentoRepositorio;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pendientes del back encontrados al integrar el front: reembolso visible en la reserva, textos de la línea de tiempo,
 * {@code puedeResenar} con la reseña de catálogo, nombres en la reserva manual y migraciones de datos.
 */
class ArreglosIntegracionTest extends PruebaIntegracion {

    @Autowired
    private PagoEventosServicio pagoEventos;
    @Autowired
    private ConsultaDocumentoRepositorio consultas;

    private String reservaConAdelanto(Turno t, String dni) throws Exception {
        String codigo = reservarWeb(t.getId(), dni).get("codigoReserva").asText();
        Reserva r = reservas.findByCodigoReserva(codigo).orElseThrow();
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, codigo, "YAPE", "COMPROBANTE",
                r.getMontoAdelanto(), "ADELANTO", "mtorres"));
        return codigo;
    }

    // ---------------------------------------------------------------- 4 / P2: reembolso

    @Test
    void canceladaConDevolucionQuedaReembolsadaConSaldoCero() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY.plusDays(3), 19), "70123321");
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"Se suspendió el partido del equipo por la lluvia.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.estadoPago").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.montoPagado").value(0.00))
                .andExpect(jsonPath("$.saldo").value(0.00))
                .andExpect(jsonPath("$.cancelacion.montoDevolucion").value(40.00));
        // El evento sigue llevando lo cobrado antes de devolver (pagos reembolsa y emite la nota de crédito con eso).
        ArgumentCaptor<ReservaCanceladaEvento> evento = ArgumentCaptor.forClass(ReservaCanceladaEvento.class);
        verify(publicador).publicar(eq(Eventos.RESERVA_CANCELADA), evento.capture());
        assertThat(evento.getValue().reserva().montoPagado()).isEqualByComparingTo("40.00");

        mvc.perform(get("/api/admin/reservas/" + codigo).header("Authorization", token("RECEPCION")))
                .andExpect(jsonPath("$.estadoPago").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.montoPagado").value(0.00))
                .andExpect(jsonPath("$.saldo").value(0.00))
                .andExpect(jsonPath("$.puedeCobrarSaldo").value(false));
        mvc.perform(get("/api/admin/reservas").header("Authorization", token("RECEPCION")).param("estadoPago", "REEMBOLSADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/interno/reservas/" + codigo).header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(jsonPath("$.estadoPago").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.saldo").value(0.00));
    }

    @Test
    void elPanelCancelaConDevolucionYLaBitacoraDiceCuantoSeDevolvio() throws Exception {
        String codigo = reservaConAdelanto(turno(2, HOY, 20), "70123321");
        mvc.perform(post("/api/admin/reservas/" + codigo + "/cancelar").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Corte de luz programado en la zona\",\"devolucion\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.montoPagado").value(0.00))
                .andExpect(jsonPath("$.saldo").value(0.00))
                .andExpect(jsonPath("$.linea[?(@.evento=='CANCELADA')].detalle").value(org.hamcrest.Matchers.hasItem(
                        "Cancelada por el complejo: Corte de luz programado en la zona. Devolución de S/ 45.00.")));
        ArgumentCaptor<pe.alcanfores.comun.eventos.AccionAuditadaEvento> auditoria =
                ArgumentCaptor.forClass(pe.alcanfores.comun.eventos.AccionAuditadaEvento.class);
        verify(publicador, org.mockito.Mockito.atLeastOnce()).publicar(eq(Eventos.AUDITORIA_ACCION), auditoria.capture());
        assertThat(auditoria.getAllValues()).anySatisfy(a -> assertThat(a.detalle()).endsWith("con devolución de S/ 45.00"));
    }

    @Test
    void canceladaSinDevolucionConservaLoPagadoPeroSinSaldo() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY, 20), "70123321");
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"70123321\",\"motivo\":\"No completamos el equipo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion.devolucion").value(false))
                .andExpect(jsonPath("$.estadoPago").value("ADELANTO_PAGADO"))
                .andExpect(jsonPath("$.montoPagado").value(40.00))
                .andExpect(jsonPath("$.saldo").value(0.00))
                .andExpect(jsonPath("$.linea[?(@.evento=='CANCELADA')].detalle").value(org.hamcrest.Matchers.hasItem(
                        "Cancelada por el cliente: No completamos el equipo.")));
    }

    // ---------------------------------------------------------------- 6: textos de la línea de tiempo

    @Test
    void laLineaDeTiempoNoDuplicaElPuntoYUsaHoraDe12Horas() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        pagoEventos.alRechazarPago(new PagoRechazadoEvento(UUID.randomUUID(), AHORA, 1L, codigo,
                "La captura no muestra el número de operación completo.", "rhuaman"));
        JsonNode detalle = json.readTree(mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String observado = detalle.at("/linea/1/detalle").asText();
        // Plazo: min(ahora + 12 h, inicio − 1 h) = 20/11/2026 22:40 → «10:40 p. m.» (12 h como la REGISTRADA).
        assertThat(observado).isEqualTo("Comprobante observado: La captura no muestra el número de operación completo. "
                + "Reenvíalo antes del 20/11/2026 a las 10:40 p. m.");
        assertThat(detalle.at("/linea/0/detalle").asText()).contains("a. m.").doesNotContain("..");
    }

    @Test
    void fraseAgregaElPuntoSoloSiFalta() {
        assertThat(ReglasReserva.frase("Se suspendió por la lluvia.")).isEqualTo("Se suspendió por la lluvia.");
        assertThat(ReglasReserva.frase("  Se suspendió por la lluvia  ")).isEqualTo("Se suspendió por la lluvia.");
        assertThat(ReglasReserva.frase("¿Pueden cambiarme la hora?")).isEqualTo("¿Pueden cambiarme la hora?");
        assertThat(ReglasReserva.frase(null)).isEmpty();
        assertThat(ReglasReserva.textoObservado("Monto incorrecto", null)).isEqualTo("Comprobante observado: Monto incorrecto.");
    }

    // ---------------------------------------------------------------- 2: puedeResenar con la reseña de catálogo

    @Test
    void puedeResenarEsFalsoSiCatalogoYaTieneLaResena() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY, 12), "70123321");
        fijarReloj(HOY.atTime(13, 10));
        when(catalogo.tieneResena(codigo)).thenReturn(false);
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(jsonPath("$.puedeResenar").value(true));
        when(catalogo.tieneResena(codigo)).thenReturn(true);
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(jsonPath("$.puedeResenar").value(false));
    }

    @Test
    void siLaReservaAunNoPermiteResenarNoSePreguntaACatalogo() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        mvc.perform(get("/api/reservas/" + codigo).param("dni", "70123321"))
                .andExpect(jsonPath("$.puedeResenar").value(false));
        verify(catalogo, never()).tieneResena(anyString());
    }

    // ---------------------------------------------------------------- P3: nombres en la reserva manual

    private String manual(long idTurno, String dni, String nombres, String apellidos) {
        return """
                {"idTurno": %d, "cliente": {"dni": "%s", "nombres": "%s", "apellidos": "%s",
                 "telefono": "987111222", "correo": "nuevo@correo.pe"}, "origen": "PRESENCIAL"}
                """.formatted(idTurno, dni, nombres, apellidos);
    }

    @Test
    void laReservaManualCorrigeLosNombresDeUnClienteNoVerificado() throws Exception {
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "99010003");
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manual(turno(2, HOY.plusDays(1), 19).getId(), "99010003", "lucía", "mendoza  paredes")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.nombres").value("Lucía"))
                .andExpect(jsonPath("$.cliente.apellidos").value("Mendoza Paredes"));
        assertThat(jdbc.queryForMap("SELECT nombres, apellidos, telefono, correo FROM cliente WHERE dni = '99010003'"))
                .containsEntry("nombres", "Lucía").containsEntry("apellidos", "Mendoza Paredes")
                .containsEntry("telefono", "987111222").containsEntry("correo", "nuevo@correo.pe");
    }

    @Test
    void laReservaManualConservaLosNombresDeRENIEC() throws Exception {
        ConsultaDocumento c = new ConsultaDocumento();
        c.setClave(new ConsultaDocumento.Clave(TipoDocumento.DNI, "99010004"));
        c.setFuente("RENIEC (Factiliza)");
        c.setRespuesta("{}");
        c.setConsultadoEn(AHORA);
        consultas.save(c);
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "99010004");
        assertThat(jdbc.queryForObject("SELECT verificado_reniec FROM cliente WHERE dni = '99010004'", Boolean.class)).isTrue();
        mvc.perform(post("/api/admin/reservas").header("Authorization", token("RECEPCION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manual(turno(2, HOY.plusDays(1), 19).getId(), "99010004", "Otro", "Nombre Distinto")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.nombres").value("Diego Armando"))
                .andExpect(jsonPath("$.cliente.apellidos").value("Ramos Quispe"));
        assertThat(jdbc.queryForObject("SELECT telefono FROM cliente WHERE dni = '99010004'", String.class)).isEqualTo("987111222");
    }

    @Test
    void laReservaWebNoCambiaLosNombresDeUnClienteExistente() throws Exception {
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "99010006");
        String otra = cuerpoReserva(turno(2, HOY.plusDays(1), 19).getId(), "99010006")
                .replace("diego  armando", "Suplantador").replace("ramos quispe", "Ajeno");
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(otra))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.nombres").value("Diego Armando"));
    }

    // ---------------------------------------------------------------- migraciones de datos (BD ya creadas)

    private void ejecutarMigracion(String archivo) throws Exception {
        String sql = new ClassPathResource("db/migration/" + archivo).getContentAsString(StandardCharsets.UTF_8);
        String sinComentarios = sql.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
        Arrays.stream(sinComentarios.split(";"))
                .map(String::strip)
                .filter(s -> s.startsWith("UPDATE"))
                .forEach(jdbc::update);
    }

    @Test
    void v2CorrigeLasReservasYaCanceladasConDevolucion() throws Exception {
        String codigo = reservaConAdelanto(turno(1, HOY.plusDays(3), 19), "70123321");
        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123321\",\"motivo\":\"No completamos el equipo\"}")).andExpect(status().isOk());
        // Así quedaban antes del arreglo.
        jdbc.update("UPDATE reserva SET estado_pago = 'ADELANTO_PAGADO', monto_pagado = 40.00 WHERE codigo_reserva = ?", codigo);
        ejecutarMigracion("V2__estado_pago_reembolsado.sql");
        assertThat(jdbc.queryForMap("SELECT estado_pago, monto_pagado FROM reserva WHERE codigo_reserva = ?", codigo))
                .containsEntry("estado_pago", "REEMBOLSADO");
        assertThat(jdbc.queryForObject("SELECT monto_pagado FROM reserva WHERE codigo_reserva = ?", BigDecimal.class, codigo))
                .isEqualByComparingTo("0.00");
        assertThat(reservas.findByCodigoReserva(codigo).orElseThrow().getEstadoPago()).isEqualTo(EstadoPago.REEMBOLSADO);
    }

    @Test
    void v3QuitaElDoblePuntoDeLosHitosGuardados() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        long id = reservas.findByCodigoReserva(codigo).orElseThrow().getId();
        String[][] filas = {
                {"OBSERVADO", "Comprobante observado: La captura no muestra el número.. Reenvíalo antes del 19/11/2026 10:40."},
                {"CANCELADA", "Cancelada por el cliente: Se suspendió por la lluvia.. Devolución de S/ 45.00."},
                {"CANCELADA", "Cancelada por el sistema: Venció el plazo para pagar.."},
                {"CANCELADA", "Cancelada por el cliente: texto recortado por largo..."}};
        for (String[] f : filas) {
            jdbc.update("INSERT INTO reserva_historial (id_reserva, evento, detalle, fecha) VALUES (?, ?, ?, ?)", id, f[0], f[1], AHORA);
        }
        ejecutarMigracion("V3__textos_linea_tiempo.sql");
        assertThat(jdbc.queryForList("SELECT detalle FROM reserva_historial WHERE id_reserva = ? AND evento <> 'REGISTRADA' "
                + "ORDER BY id_historial", String.class, id)).containsExactly(
                "Comprobante observado: La captura no muestra el número. Reenvíalo antes del 19/11/2026 10:40.",
                "Cancelada por el cliente: Se suspendió por la lluvia. Devolución de S/ 45.00.",
                "Cancelada por el sistema: Venció el plazo para pagar.",
                "Cancelada por el cliente: texto recortado por largo...");
    }
}
