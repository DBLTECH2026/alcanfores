package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PagoMp;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PreferenciaMp;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.evento.ConsumidorReservaReprogramada;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.pasarela.simulador.FirmaHmac;
import pe.alcanfores.pagos.servicio.AprobacionEnLinea;
import pe.alcanfores.pagos.servicio.ReprogramacionReservaServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reserva reprogramada: pagos consume {@code reserva.reprogramada} (intentos abiertos → EXPIRADO, pagos en revisión
 * con el horario nuevo) y, al aprobar un pago en línea, vuelve a mirar la reserva para no cobrar un monto viejo.
 */
class ReprogramacionPagosTest extends IntegracionBase {

    static final String OTRA = "ALC-QW3RT7";
    static final String SECRETO_MP = "secreto-webhook-mp-pruebas";
    static final String SECRETO_BETA = "secreto-de-pruebas-pasarela-beta";
    static final String MOTIVO_COBRADO = AprobacionEnLinea.MOTIVO_HORARIO + " Te devolveremos lo cobrado.";

    @Autowired
    ObjectMapper json;
    @Autowired
    ConsumidorReservaReprogramada consumidor;
    @Autowired
    ReprogramacionReservaServicio reprogramaciones;
    @Autowired
    PagoRepositorio pagoRepositorio;
    @Autowired
    PlatformTransactionManager transacciones;

    // ---------------------------------------------------------------- ayudas

    /** La misma reserva {@link #CODIGO} ya cambiada a la Cancha 1 · Fútbol 5, pasado mañana + 1, 8:00–9:00 p. m. */
    private static ReservaInterna reprogramada(String total, String adelanto) {
        LocalDate fecha = Tiempo.hoy().plusDays(3);
        BigDecimal t = new BigDecimal(total);
        return new ReservaInterna(10L, CODIGO, 1L, "Cancha 1", "FUTBOL_5", fecha, LocalTime.of(20, 0), LocalTime.of(21, 0),
                t, new BigDecimal(adelanto), BigDecimal.ZERO,
                new ClienteContacto(DNI, "Diego Alonso", "Ramos Quispe", "987654321", "diego.ramos@correo.pe"),
                "PENDIENTE", "SIN_PAGO", t, LocalDateTime.of(fecha, LocalTime.of(20, 0)), "8:00–9:00 p. m.", 1);
    }

    private static ReservaReprogramadaEvento evento(UUID id) {
        return evento(id, "100.00", "40.00", Tiempo.ahora());
    }

    /** {@code reserva.reprogramada} de {@link #CODIGO} a la reserva de {@link #reprogramada} con ese total y adelanto. */
    private static ReservaReprogramadaEvento evento(UUID id, String total, String adelanto, LocalDateTime ocurridoEn) {
        LocalDate fecha = Tiempo.hoy().plusDays(3);
        DatosReserva d = new DatosReserva(10L, CODIGO, 1L, "Cancha 1", "FUTBOL_5", fecha, LocalTime.of(20, 0),
                LocalTime.of(21, 0), new BigDecimal(total), new BigDecimal(adelanto), BigDecimal.ZERO,
                new ClienteContacto(DNI, "Diego Alonso", "Ramos Quispe", "987654321", "diego.ramos@correo.pe"),
                "8:00–9:00 p. m.", 1);
        return new ReservaReprogramadaEvento(id, ocurridoEn, d, "PENDIENTE", new BigDecimal(total), "Cancha 2",
                Tiempo.hoy().plusDays(2), LocalTime.of(19, 0), LocalTime.of(20, 0), "7:00–8:00 p. m.",
                new BigDecimal("90.00"), ReservaReprogramadaEvento.PANEL, "El cliente pidió otra cancha", "EN_REVISION", null);
    }

    private String intentoBeta(String codigo, String tipoPago) throws Exception {
        String r = mvc.perform(post("/api/pagos/pasarela/intentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + codigo + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"" + tipoPago + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("referencia").asText();
    }

    private String preferenciaMp(String tipoPago) throws Exception {
        when(mercadoPago.crearPreferencia(any(), anyString())).thenReturn(new PreferenciaMp("pref-1",
                "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1",
                "https://sandbox.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1"));
        String r = mvc.perform(post("/api/pagos/mercadopago/preferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"" + tipoPago + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("referencia").asText();
    }

    private ResultActions pagarConTarjeta(String ref) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("numero", "4111 1111 1111 1111", "titular", "Diego Ramos",
                "mesVencimiento", 12, "anioVencimiento", 30, "cvv", "123", "correo", "diego.ramos@correo.pe"));
        return mvc.perform(post("/api/pasarela/intentos/{ref}/tarjeta", ref).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions webhookMp(String idPago, String ref) throws Exception {
        when(mercadoPago.obtenerPago(idPago)).thenReturn(Optional.of(pagoMp(idPago, ref)));
        String ts = "1731963000";
        String firma = "ts=" + ts + ",v1=" + FirmaHmac.firmar(SECRETO_MP, "id:" + idPago + ";request-id:req-" + idPago + ";ts:" + ts + ";");
        return mvc.perform(post("/api/webhooks/mercadopago").param("data.id", idPago).param("type", "payment")
                .header("x-signature", firma).header("x-request-id", "req-" + idPago)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\",\"data\":{\"id\":\"" + idPago + "\"}}"));
    }

    private static PagoMp pagoMp(String id, String ref) {
        return new PagoMp(id, "approved", "accredited", ref, new BigDecimal("45.00"), "visa", "credit_card", "654321", "4242");
    }

    private ResultActions webhookBeta(String ref, String idEvento) throws Exception {
        String cuerpo = "{\"idEvento\":\"" + idEvento + "\",\"tipo\":\"pago.aprobado\",\"referencia\":\"" + ref
                + "\",\"idTransaccion\":\"tx-" + idEvento + "\",\"metodo\":\"TARJETA\",\"marca\":\"Visa\",\"ultimos4\":\"1111\","
                + "\"codigoAutorizacion\":\"123456\"}";
        return mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Firma", FirmaHmac.firmar(SECRETO_BETA, cuerpo)).header("X-Evento-Id", idEvento));
    }

    private Map<String, Object> intento(String ref) {
        return jdbc.queryForMap("SELECT estado, motivo_rechazo, id_externo, id_pago FROM intento_pago WHERE referencia = ?", ref);
    }

    /** Avisos de bitácora «pago en línea no aplicado» publicados hasta ahora. */
    private List<AccionAuditadaEvento> auditorias() {
        ArgumentCaptor<AccionAuditadaEvento> captor = ArgumentCaptor.forClass(AccionAuditadaEvento.class);
        verify(publicador, atLeast(0)).publicar(eq(Eventos.AUDITORIA_ACCION), captor.capture());
        return captor.getAllValues().stream().filter(a -> "PAGO_EN_LINEA_NO_APLICADO".equals(a.accion())).toList();
    }

    // ---------------------------------------------------------------- 1: consumidor de reserva.reprogramada

    @Test
    void laReprogramacionExpiraLosIntentosAbiertosYRefrescaElPagoEnRevision() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String beta = intentoBeta(CODIGO, "ADELANTO");
        String mp = preferenciaMp("TOTAL");
        // la preferencia cerró el intento beta; se reabre para tener dos intentos abiertos (beta en 3-D Secure y MP)
        jdbc.update("UPDATE intento_pago SET estado = 'REQUIERE_3DS', motivo_rechazo = NULL WHERE referencia = ?", beta);
        mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", png(300, 600)))
                        .param("codigoReserva", CODIGO).param("dni", DNI).param("metodo", "YAPE")
                        .param("codigoOperacion", "77441122"))
                .andExpect(status().isCreated());
        reservaDisponible(reserva(OTRA, "PENDIENTE", "SIN_PAGO", "0"));
        String deOtraReserva = intentoBeta(OTRA, "ADELANTO");
        assertThat(jdbc.queryForObject("SELECT concepto FROM pago", String.class)).contains("Cancha 2", "7:00–8:00 p. m.");

        UUID id = UUID.randomUUID();
        consumidor.alReprogramarReserva(evento(id));

        for (String ref : List.of(beta, mp)) {
            assertThat(intento(ref)).containsEntry("estado", "EXPIRADO")
                    .containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_HORARIO);
        }
        assertThat(intento(deOtraReserva)).containsEntry("estado", "CREADO");
        String fecha = Tiempo.hoy().plusDays(3).format(Tiempo.FECHA_CORTA);
        Map<String, Object> pago = jdbc.queryForMap(
                "SELECT estado_validacion, concepto, porcentaje_adelanto, cliente_dni, cliente_nombres FROM pago");
        // el adelanto se recalculó con el % vigente (40 %): también se refresca
        assertThat(pago).containsEntry("estado_validacion", "PENDIENTE").containsEntry("porcentaje_adelanto", 40)
                .containsEntry("cliente_dni", DNI).containsEntry("cliente_nombres", "Diego Alonso");
        assertThat((String) pago.get("concepto")).startsWith("Alquiler de Cancha 1 · Fútbol 5 · ")
                .endsWith(fecha + " 8:00–9:00 p. m.");

        // la página de resultado y el checkout muestran el motivo (no «venció el tiempo»)
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", beta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADO"))
                .andExpect(jsonPath("$.motivoRechazo").value(AprobacionEnLinea.MOTIVO_HORARIO));
        pagarConTarjeta(beta).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_EXPIRADO"))
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_HORARIO));

        // idempotente: el mismo evento otra vez no toca nada
        jdbc.update("UPDATE intento_pago SET estado = 'CREADO', motivo_rechazo = NULL WHERE referencia = ?", mp);
        assertThat(reprogramaciones.procesar(evento(id))).isFalse();
        assertThat(intento(mp)).containsEntry("estado", "CREADO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_procesado WHERE tipo = ?", Integer.class,
                Eventos.RESERVA_REPROGRAMADA)).isEqualTo(1);

        // al validar el comprobante, la boleta sale con el horario nuevo
        long idPago = jdbc.queryForObject("SELECT id_pago FROM pago", Long.class);
        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        assertThat(jdbc.queryForObject("SELECT descripcion FROM comprobante", String.class))
                .startsWith("Alquiler de Cancha 1 · Fútbol 5 · ").contains(fecha + " 8:00–9:00 p. m.")
                .doesNotContain("Cancha 2");
    }

    @Test
    void elEventoEsperaAUnaValidacionEnCursoYNoDevuelveElPagoAPendiente() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", png(300, 600)))
                        .param("codigoReserva", CODIGO).param("dni", DNI).param("metodo", "PLIN")
                        .param("codigoOperacion", "66554433"))
                .andExpect(status().isCreated());
        long idPago = jdbc.queryForObject("SELECT id_pago FROM pago", Long.class);
        TransactionTemplate tx = new TransactionTemplate(transacciones);
        CountDownLatch bloqueado = new CountDownLatch(1);
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            // recepción valida el pago: tiene la fila bloqueada mientras llega reserva.reprogramada
            Future<?> validacion = hilo.submit(() -> tx.executeWithoutResult(estado -> {
                Pago p = pagoRepositorio.bloquear(idPago).orElseThrow();
                bloqueado.countDown();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                p.validar(Tiempo.ahora(), 2L, "mtorres");
            }));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            consumidor.alReprogramarReserva(evento(UUID.randomUUID()));
            validacion.get(10, TimeUnit.SECONDS);
        } finally {
            hilo.shutdownNow();
        }
        // el evento esperó y vio el pago ya validado: no lo reescribe (ni lo devuelve a PENDIENTE)
        assertThat(jdbc.queryForMap("SELECT estado_validacion, validado_por, concepto FROM pago"))
                .containsEntry("estado_validacion", "VALIDADO").containsEntry("validado_por", "mtorres");
    }

    // ---------------------------------------------------------------- 2: aprobar vuelve a mirar la reserva

    @Test
    void elWebhookNoApruebaUnMontoMayorAlSaldoVigenteYAvisaUnaSolaVez() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("ADELANTO");
        // reservas ya cambió a un horario más barato (S/ 40) y el evento aún no llega a pagos
        reservaDisponible(reprogramada("40.00", "20.00"));

        webhookMp("1325", ref).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicado").value(false))
                .andExpect(jsonPath("$.resultado").value(org.hamcrest.Matchers.startsWith("No aplicado")));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante", Integer.class)).isZero();
        assertThat(intento(ref)).containsEntry("estado", "EXPIRADO").containsEntry("motivo_rechazo", MOTIVO_COBRADO)
                .containsEntry("id_externo", "1325");
        assertThat(intento(ref).get("id_pago")).isNull();
        verify(publicador, never()).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
        List<AccionAuditadaEvento> avisos = auditorias();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).accion()).isEqualTo("PAGO_EN_LINEA_NO_APLICADO");
        assertThat(avisos.get(0).detalle()).contains("S/ 45.00", "S/ 40.00", ref, "1325", "devolverlo");

        // «Ya pagué, verificar»: Mercado Pago sigue diciendo approved, pero no se registra ni se repite el aviso
        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of(pagoMp("1325", ref)));
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADO"))
                .andExpect(jsonPath("$.motivoRechazo").value(MOTIVO_COBRADO))
                .andExpect(jsonPath("$.comprobante").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(auditorias()).hasSize(1);
    }

    @Test
    void laPasarelaBetaNoCobraElMontoViejo() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = intentoBeta(CODIGO, "TOTAL");
        reservaDisponible(reprogramada("60.00", "30.00"));

        pagarConTarjeta(ref).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_EXPIRADO"))
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_HORARIO));
        // no llegó a cobrar: sin transacción del proveedor, sin pago y sin aviso de devolución
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pasarela_transaccion", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(intento(ref)).containsEntry("estado", "EXPIRADO")
                .containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_HORARIO);
        assertThat(auditorias()).isEmpty();
        mvc.perform(get("/api/pasarela/intentos/{ref}", ref)).andExpect(jsonPath("$.estado").value("EXPIRADO"));
    }

    @Test
    void antesDeCobrarLaBetaNoAceptaUnMontoViejoMenorAlVigente() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String adelantoViejo = intentoBeta(CODIGO, "ADELANTO");
        // se cambió a un horario más caro (S/ 120, adelanto S/ 60): el adelanto viejo de S/ 45 cabe en el saldo, pero
        // cobrarlo dejaría la reserva CONFIRMADA con menos del adelanto (lo que REPROGRAMAR_ADELANTO_INSUFICIENTE evita)
        reservaDisponible(reprogramada("120.00", "60.00"));
        pagarConTarjeta(adelantoViejo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_EXPIRADO"))
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_HORARIO));
        assertThat(intento(adelantoViejo)).containsEntry("estado", "EXPIRADO")
                .containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_HORARIO);

        // un «Pago total» de S/ 90 tampoco: ya no cubre el total de S/ 120
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String totalViejo = intentoBeta(CODIGO, "TOTAL");
        reservaDisponible(reprogramada("120.00", "60.00"));
        pagarConTarjeta(totalViejo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_HORARIO));

        // nada se cobró: sin transacción del proveedor, sin pago y sin aviso de devolución
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pasarela_transaccion", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(auditorias()).isEmpty();

        // el intento nuevo sale con el adelanto vigente y se cobra normalmente
        String nuevo = intentoBeta(CODIGO, "ADELANTO");
        pagarConTarjeta(nuevo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));
        assertThat(jdbc.queryForObject("SELECT monto FROM pago", BigDecimal.class)).isEqualByComparingTo("60.00");
    }

    @Test
    void siElProveedorYaCobroUnMontoQueCabeSeRegistraConElHorarioNuevo() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("ADELANTO");
        // se cambió a un horario más caro (S/ 120): Mercado Pago ya cobró el adelanto viejo de S/ 45, que cabe en el
        // saldo; al aprobar la regla sigue siendo «no más que el saldo» (no se pierde un cobro hecho)
        reservaDisponible(reprogramada("120.00", "60.00"));

        webhookMp("1600", ref).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("Pago aprobado y registrado"));
        Map<String, Object> pago = jdbc.queryForMap("SELECT estado_validacion, monto, concepto, porcentaje_adelanto FROM pago");
        assertThat(pago).containsEntry("estado_validacion", "VALIDADO").containsEntry("porcentaje_adelanto", 50);
        assertThat((BigDecimal) pago.get("monto")).isEqualByComparingTo("45.00");
        assertThat((String) pago.get("concepto")).contains("Cancha 1", "8:00–9:00 p. m.");
        assertThat(jdbc.queryForObject("SELECT descripcion FROM comprobante", String.class))
                .contains("Cancha 1", "8:00–9:00 p. m.").doesNotContain("Cancha 2");
        assertThat(intento(ref)).containsEntry("estado", "APROBADO");
    }

    // ---------------------------------------------------------------- 3: arreglos de la revisión

    @Test
    void elNoAplicadoEsDefinitivoAunqueOtroCambioDejeSaldo() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("ADELANTO");
        reservaDisponible(reprogramada("40.00", "20.00"));
        webhookMp("1325", ref).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value(org.hamcrest.Matchers.startsWith("No aplicado")));
        assertThat(auditorias()).hasSize(1);

        // antes de que recepción devuelva el dinero, el panel vuelve a cambiar la reserva, ahora a S/ 120
        reservaDisponible(reprogramada("120.00", "60.00"));
        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of(pagoMp("1325", ref)));
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADO"))
                .andExpect(jsonPath("$.motivoRechazo").value(MOTIVO_COBRADO))
                .andExpect(jsonPath("$.comprobante").doesNotExist());
        // la operación 1325 sigue sin registrarse (la bitácora pidió devolverla) y no se repite el aviso
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante", Integer.class)).isZero();
        verify(publicador, never()).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
        assertThat(auditorias()).hasSize(1);
        assertThat(intento(ref)).containsEntry("estado", "EXPIRADO").containsEntry("id_externo", "1325");

        // otra operación del proveedor (otro cobro con la misma preferencia) sí se evalúa: S/ 45 cabe en S/ 120
        webhookMp("1326", ref).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("Pago aprobado y registrado"));
        assertThat(jdbc.queryForObject("SELECT codigo_operacion FROM pago", String.class)).isEqualTo("1326");
        assertThat(auditorias()).hasSize(1);
    }

    @Test
    void elNoAplicadoPorSaldoNoSeRegistraSiDespuesSeAnulaElCobroDeCaja() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("TOTAL");
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"ADELANTO\",\"metodo\":\"EFECTIVO\",\"monto\":45.00}"))
                .andExpect(status().isCreated());
        webhookMp("1400", ref).andExpect(status().isOk());
        assertThat(intento(ref)).containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_SALDO + " Te devolveremos lo cobrado.");

        // se anula el cobro de caja: el saldo vuelve a S/ 90 y el «Ya pagué» encuentra la operación 1400
        jdbc.update("UPDATE pago SET estado_validacion = 'ANULADO' WHERE canal = 'CAJA'");
        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of(pagoMp("1400", ref)));
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE canal = 'PASARELA'", Integer.class)).isZero();
        assertThat(auditorias()).hasSize(1);
    }

    @Test
    void unEventoAtrasadoNoExpiraElIntentoCreadoDespuesDelCambio() throws Exception {
        // reservas ya tiene el horario nuevo (S/ 100, adelanto S/ 40) y el cliente crea el pago con el monto correcto
        reservaDisponible(reprogramada("100.00", "40.00"));
        String ref = intentoBeta(CODIGO, "ADELANTO");
        // llega tarde el evento de ESE cambio (reintentos con espera, cola acumulada o DLQ): ocurrió 5 minutos antes
        consumidor.alReprogramarReserva(evento(UUID.randomUUID(), "100.00", "40.00", Tiempo.ahora().minusMinutes(5)));
        assertThat(intento(ref)).containsEntry("estado", "CREADO");
        assertThat(intento(ref).get("motivo_rechazo")).isNull();

        // eventos en desorden: el cliente ya pagaba sobre un segundo cambio (S/ 120) y llega el del primero (S/ 100)
        reservaDisponible(reprogramada("120.00", "60.00"));
        String sobreElSegundo = intentoBeta(CODIGO, "ADELANTO");
        consumidor.alReprogramarReserva(evento(UUID.randomUUID(), "100.00", "40.00", Tiempo.ahora().minusMinutes(3)));
        assertThat(intento(sobreElSegundo)).containsEntry("estado", "CREADO");

        // relojes o segundos que se cruzan: el evento dice «después», pero el intento ya es el de ese horario
        reservaDisponible(reprogramada("100.00", "40.00"));
        String alDia = intentoBeta(CODIGO, "ADELANTO");
        consumidor.alReprogramarReserva(evento(UUID.randomUUID(), "100.00", "40.00", Tiempo.ahora().plusMinutes(1)));
        assertThat(intento(alDia)).containsEntry("estado", "CREADO");

        pagarConTarjeta(alDia).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));
        assertThat(jdbc.queryForObject("SELECT monto FROM pago", BigDecimal.class)).isEqualByComparingTo("40.00");
    }

    @Test
    void cambioAlMismoPrecioCierraConUnMotivoNeutro() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = intentoBeta(CODIGO, "ADELANTO");
        // el panel la pasa a la Cancha 1 a otra hora, del MISMO precio (S/ 90, adelanto S/ 45)
        consumidor.alReprogramarReserva(evento(UUID.randomUUID(), "90.00", "45.00", Tiempo.ahora()));

        assertThat(intento(ref)).containsEntry("estado", "EXPIRADO")
                .containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_CAMBIO_HORARIO);
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoRechazo").value(AprobacionEnLinea.MOTIVO_CAMBIO_HORARIO));
        pagarConTarjeta(ref).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_EXPIRADO"))
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_CAMBIO_HORARIO));

        // si el evento aún no llega, la beta tampoco cobra con el horario viejo y usa el mismo motivo neutro
        String antesDelEvento = intentoBeta(CODIGO, "ADELANTO");
        reservaDisponible(reprogramada("90.00", "45.00"));
        pagarConTarjeta(antesDelEvento).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(AprobacionEnLinea.MOTIVO_CAMBIO_HORARIO));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pasarela_transaccion", Integer.class)).isZero();
    }

    @Test
    void sinCambioDeHorarioElMotivoHablaDelSaldo() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("TOTAL");
        // mientras el cliente estaba en Mercado Pago, recepción cobró el adelanto en caja: el total de S/ 90 ya no cabe
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"ADELANTO\",\"metodo\":\"EFECTIVO\",\"monto\":45.00}"))
                .andExpect(status().isCreated());

        webhookMp("1400", ref).andExpect(status().isOk());
        assertThat(intento(ref)).containsEntry("estado", "EXPIRADO")
                .containsEntry("motivo_rechazo", AprobacionEnLinea.MOTIVO_SALDO + " Te devolveremos lo cobrado.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE canal = 'PASARELA'", Integer.class)).isZero();
    }

    @Test
    void siReservasNoRespondeElAvisoFallaSinRegistrarYElReintentoAprueba() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = intentoBeta(CODIGO, "ADELANTO");
        doThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "El servicio de reservas no responde."))
                .when(reservas).buscar(CODIGO);

        webhookBeta(ref, "ev-caido").andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_evento", Integer.class)).isZero();
        assertThat(intento(ref)).containsEntry("estado", "CREADO");

        // la pasarela reintenta el MISMO aviso cuando reservas vuelve
        doReturn(Optional.of(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"))).when(reservas).buscar(CODIGO);
        webhookBeta(ref, "ev-caido").andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("Pago aprobado y registrado"));
        assertThat(intento(ref)).containsEntry("estado", "APROBADO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isEqualTo(1);
    }

    @Test
    void siReservasNoConoceLaReservaSeApruebaConLaFotografiaDelIntento() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String ref = preferenciaMp("ADELANTO");
        doReturn(Optional.empty()).when(reservas).buscar(CODIGO);

        webhookMp("1500", ref).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("Pago aprobado y registrado"));
        assertThat(jdbc.queryForObject("SELECT concepto FROM pago", String.class)).contains("Cancha 2", "7:00–8:00 p. m.");
    }
}
