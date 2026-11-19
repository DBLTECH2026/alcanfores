package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PagoMp;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PreferenciaMp;
import pe.alcanfores.pagos.pasarela.simulador.FirmaHmac;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mercado Pago (sandbox) con el cliente REST simulado: preferencia, webhook firmado y consulta por referencia. */
class MercadoPagoTest extends IntegracionBase {

    static final String SECRETO = "secreto-webhook-mp-pruebas";

    @Autowired
    ObjectMapper json;

    private String crearPreferencia() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        when(mercadoPago.crearPreferencia(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(new PreferenciaMp("pref-1",
                "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1",
                "https://sandbox.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1"));
        String r = mvc.perform(post("/api/pagos/mercadopago/preferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"ADELANTO\","
                                + "\"comprobante\":{\"tipo\":\"BOLETA\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.initPoint").value("https://sandbox.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref-1"))
                .andExpect(jsonPath("$.sandbox").value(true))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("referencia").asText();
    }

    private static PagoMp pagoMp(String id, String estado, String detalle, String referencia) {
        return new PagoMp(id, estado, detalle, referencia, new BigDecimal("45.00"), "visa", "credit_card", "654321", "4242");
    }

    private static String firma(String dataId, String idSolicitud, String ts) {
        return "ts=" + ts + ",v1=" + FirmaHmac.firmar(SECRETO, "id:" + dataId + ";request-id:" + idSolicitud + ";ts:" + ts + ";");
    }

    @Test
    void sinTokenNoSeOfreceYResponde409() throws Exception {
        propiedades.getMercadopago().setAccessToken("");
        mvc.perform(get("/api/pagos/metodos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mercadoPago").value(false))
                .andExpect(jsonPath("$.tarjetaBeta").value(true))
                .andExpect(jsonPath("$.yape.numero").value("987654321"))
                .andExpect(jsonPath("$.tarjetasPrueba.length()").value(7))
                .andExpect(jsonPath("$.yapePrueba.codigoAprueba").value("123456"));
        mvc.perform(post("/api/pagos/mercadopago/preferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"ADELANTO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MP_NO_CONFIGURADO"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void laPreferenciaLlevaReferenciaYRetornoSinNotificacionEnLocalhost() throws Exception {
        String ref = crearPreferencia();
        ArgumentCaptor<Map<String, Object>> cuerpo = ArgumentCaptor.forClass(Map.class);
        verify(mercadoPago).crearPreferencia(cuerpo.capture(), eq(ref));
        Map<String, Object> c = cuerpo.getValue();
        assertThat(c.get("external_reference")).isEqualTo(ref);
        assertThat(((Map<String, String>) c.get("back_urls")).get("success"))
                .isEqualTo("http://localhost:5186/pago/resultado?ref=" + ref);
        assertThat(c).doesNotContainKeys("notification_url", "auto_return");
        Map<String, Object> item = ((List<Map<String, Object>>) c.get("items")).get(0);
        assertThat(item.get("unit_price")).isEqualTo(new BigDecimal("45.00"));
        assertThat(item.get("currency_id")).isEqualTo("PEN");
        assertThat(((Map<String, Object>) c.get("payer")).get("email")).isEqualTo("diego.ramos@correo.pe");
    }

    @Test
    void webhookFirmadoApruebaUnaSolaVez() throws Exception {
        String ref = crearPreferencia();
        when(mercadoPago.obtenerPago("1325")).thenReturn(Optional.of(pagoMp("1325", "approved", "accredited", ref)));

        mvc.perform(post("/api/webhooks/mercadopago").param("data.id", "1325").param("type", "payment")
                        .header("x-signature", "ts=1,v1=abc").header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\",\"data\":{\"id\":\"1325\"}}"))
                .andExpect(status().isUnauthorized());

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/webhooks/mercadopago").param("data.id", "1325").param("type", "payment")
                            .header("x-signature", firma("1325", "req-1", "1731963000")).header("x-request-id", "req-1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\",\"data\":{\"id\":\"1325\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.duplicado").value(i == 1));
        }
        Map<String, Object> pago = jdbc.queryForMap("SELECT metodo, validado_por, codigo_operacion, ultimos4 FROM pago");
        assertThat(pago).containsEntry("metodo", "MERCADO_PAGO").containsEntry("validado_por", "Mercado Pago")
                .containsEntry("codigo_operacion", "1325").containsEntry("ultimos4", "4242");
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref))
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
    }

    @Test
    void avisosQueNoSonDePagoSeIgnoran() throws Exception {
        mvc.perform(post("/api/webhooks/mercadopago").param("data.id", "77").param("type", "merchant_order")
                        .header("x-signature", firma("77", "req-9", "1")).header("x-request-id", "req-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("Tipo de aviso ignorado"));
    }

    @Test
    void yaPagueVerificarConsultaPorReferencia() throws Exception {
        String ref = crearPreferencia();
        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of());
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(jsonPath("$.estado").value("CREADO"));

        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of(pagoMp("88", "rejected", "cc_rejected_insufficient_amount", ref)));
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref))
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("Fondos insuficientes."));

        when(mercadoPago.buscarPorReferencia(ref)).thenReturn(List.of(pagoMp("89", "approved", "accredited", ref),
                pagoMp("88", "rejected", "cc_rejected_insufficient_amount", ref)));
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref))
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.proveedor").value("MERCADO_PAGO"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isEqualTo(1);
    }
}
