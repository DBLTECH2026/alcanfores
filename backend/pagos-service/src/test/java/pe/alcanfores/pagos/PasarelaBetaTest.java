package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.pasarela.simulador.FirmaHmac;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alcanfores Pay (beta) de punta a punta: checkout → simulador → webhook firmado → pago validado + comprobante. */
class PasarelaBetaTest extends IntegracionBase {

    @Autowired
    ObjectMapper json;

    private String crearIntento(String tipoPago) throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String r = mvc.perform(post("/api/pagos/pasarela/intentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"" + tipoPago + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referencia").value(matchesPattern("ALP-[A-HJ-NP-Z2-9]{10}")))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("referencia").asText();
    }

    private ResultActions pagar(String ref, String numero, String cvv) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("numero", numero, "titular", "Diego Ramos", "mesVencimiento", 12,
                "anioVencimiento", 30, "cvv", cvv, "correo", "diego.ramos@correo.pe"));
        return mvc.perform(post("/api/pasarela/intentos/{ref}/tarjeta", ref).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String estado(String ref) throws Exception {
        return json.readTree(mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("estado").asText();
    }

    @Test
    void tarjetaAprobadaRegistraElPagoPorWebhook() throws Exception {
        String ref = crearIntento("TOTAL");
        mvc.perform(get("/api/pasarela/intentos/{ref}", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(90.00))
                .andExpect(jsonPath("$.comercio").value("Complejo Deportivo Los Alcanfores"))
                .andExpect(jsonPath("$.estado").value("CREADO"));

        pagar(ref, "4111 1111 1111 1111", "123").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.marca").value("Visa"))
                .andExpect(jsonPath("$.ultimos4").value("1111"))
                .andExpect(jsonPath("$.codigoAutorizacion").value(matchesPattern("\\d{6}")));

        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        Map<String, Object> pago = jdbc.queryForMap("SELECT * FROM pago");
        assertThat(pago.get("estado_validacion")).isEqualTo("VALIDADO");
        assertThat(pago.get("validado_por")).isEqualTo("Pasarela");
        assertThat(pago.get("canal")).isEqualTo("PASARELA");
        assertThat(pago.get("ultimos4")).isEqualTo("1111");
        verify(publicador).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
        verify(publicador).publicar(eq(Eventos.COMPROBANTE_EMITIDO), any(ComprobanteEmitidoEvento.class));

        // nunca se guarda el número completo ni el CVV
        List<String> filas = jdbc.queryForList("SELECT CONCAT_WS('|', marca, ultimos4, cuerpo_notificacion) FROM pasarela_transaccion", String.class);
        assertThat(filas).allSatisfy(f -> assertThat(f).doesNotContain("4111111111111111").doesNotContain("4111 1111"));

        pagar(ref, "4111111111111111", "123").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_PAGADO"));
    }

    @Test
    void tarjetasDePruebaRechazadas() throws Exception {
        String ref = crearIntento("ADELANTO");
        pagar(ref, "4000000000000002", "123").andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivo").value("Fondos insuficientes. Prueba con otra tarjeta."));
        assertThat(estado(ref)).isEqualTo("RECHAZADO");
        pagar(ref, "4000000000009995", "123").andExpect(jsonPath("$.motivo").value("La tarjeta está bloqueada. Comunícate con tu banco."));
        pagar(ref, "4000000000000069", "123").andExpect(jsonPath("$.motivo").value("La tarjeta está vencida."));
        // tras los rechazos puede reintentar con otra tarjeta mientras no expire
        pagar(ref, "5555555555554444", "321").andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.marca").value("Mastercard"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isEqualTo(1);
    }

    @Test
    void validacionesDeTarjeta() throws Exception {
        String ref = crearIntento("ADELANTO");
        pagar(ref, "4111111111111112", "123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("numero"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Revisa el número de tarjeta."));
        pagar(ref, "378282246310005", "123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cvv"));
        String vencida = json.writeValueAsString(Map.of("numero", "4111111111111111", "titular", "D", "mesVencimiento", 1,
                "anioVencimiento", 20, "cvv", "12", "correo", "no-es-correo"));
        mvc.perform(post("/api/pasarela/intentos/{ref}/tarjeta", ref).contentType(MediaType.APPLICATION_JSON).content(vencida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(4));
        pagar(ref, "6011111111111117", "123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Aceptamos Visa, Mastercard, American Express y Diners Club."));
        pagar(ref, "378282246310005", "1234").andExpect(status().isOk()).andExpect(jsonPath("$.marca").value("American Express"));
    }

    @Test
    void tresDSecureConCodigoCorrectoEIncorrecto() throws Exception {
        String ref = crearIntento("ADELANTO");
        mvc.perform(post("/api/pasarela/intentos/{ref}/3ds", ref).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_3DS_PENDIENTE"));
        pagar(ref, "4000002760003184", "123").andExpect(jsonPath("$.estado").value("REQUIERE_3DS"));
        assertThat(estado(ref)).isEqualTo("REQUIERE_3DS");
        mvc.perform(post("/api/pasarela/intentos/{ref}/3ds", ref).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"12\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pasarela/intentos/{ref}/3ds", ref).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"999999\"}"))
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
        assertThat(estado(ref)).isEqualTo("RECHAZADO");

        pagar(ref, "4000 0027 6000 3184", "123").andExpect(jsonPath("$.estado").value("REQUIERE_3DS"));
        mvc.perform(post("/api/pasarela/intentos/{ref}/3ds", ref).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"123456\"}"))
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        assertThat(estado(ref)).isEqualTo("APROBADO");
    }

    @Test
    void yapeBeta() throws Exception {
        String ref = crearIntento("ADELANTO");
        mvc.perform(post("/api/pasarela/intentos/{ref}/yape", ref).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"812345678\",\"codigoAprobacion\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2));
        mvc.perform(post("/api/pasarela/intentos/{ref}/yape", ref).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"+51 987 654 321\",\"codigoAprobacion\":\"000000\"}"))
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivo").value("Saldo insuficiente en Yape."));
        mvc.perform(post("/api/pasarela/intentos/{ref}/yape", ref).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\":\"987654321\",\"codigoAprobacion\":\"123456\"}"))
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        assertThat(jdbc.queryForObject("SELECT metodo FROM pago", String.class)).isEqualTo("YAPE");
    }

    @Test
    void webhookConFirmaInvalidaEs401YLaValidaEsIdempotente() throws Exception {
        String ref = crearIntento("ADELANTO");
        String cuerpo = "{\"idEvento\":\"ev-1\",\"tipo\":\"pago.aprobado\",\"referencia\":\"" + ref
                + "\",\"idTransaccion\":\"tx-1\",\"metodo\":\"TARJETA\",\"marca\":\"Visa\",\"ultimos4\":\"1111\",\"codigoAutorizacion\":\"123456\"}";

        mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("X-Firma", FirmaHmac.firmar("otro-secreto", cuerpo)).header("X-Evento-Id", "ev-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("X-Evento-Id", "ev-1"))
                .andExpect(status().isUnauthorized());

        String firma = FirmaHmac.firmar("secreto-de-pruebas-pasarela-beta", cuerpo);
        mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("X-Firma", firma).header("X-Evento-Id", "ev-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicado").value(false));
        mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("X-Firma", firma).header("X-Evento-Id", "ev-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicado").value(true));
        // otro aviso distinto pero del mismo intento ya aprobado tampoco duplica el cobro
        mvc.perform(post("/api/webhooks/pasarela-beta").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("X-Firma", firma).header("X-Evento-Id", "ev-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("El pago ya estaba aprobado"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_evento WHERE firma_valida = 0", Integer.class)).isEqualTo(2);
        verify(publicador, times(1)).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
    }

    @Test
    void intentoExpiradoNoAceptaPagos() throws Exception {
        String ref = crearIntento("ADELANTO");
        jdbc.update("UPDATE intento_pago SET expira_en = ? WHERE referencia = ?", Tiempo.ahora().minusMinutes(1), ref);
        assertThat(estado(ref)).isEqualTo("EXPIRADO");
        pagar(ref, "4111111111111111", "123").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INTENTO_EXPIRADO"));
        mvc.perform(get("/api/pasarela/intentos/ALP-NOEXISTE22")).andExpect(status().isNotFound());
    }

    @Test
    void unNuevoIntentoCierraElAnteriorYLaReservaYaPagadaNoAceptaOtro() throws Exception {
        String primero = crearIntento("ADELANTO");
        String segundo = crearIntento("ADELANTO");
        assertThat(estado(primero)).isEqualTo("EXPIRADO");
        pagar(segundo, "4111111111111111", "123").andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(post("/api/pagos/pasarela/intentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"ADELANTO\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RESERVA_YA_PAGADA"));
        mvc.perform(post("/api/pagos/pasarela/intentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"ALC-1234\",\"dni\":\"7012\",\"tipoPago\":\"CUOTAS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(3));
    }
}
