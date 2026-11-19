package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /interno/pagos/{id}/validar-ia: validación automática de Canchita (IA). */
class ValidacionIaTest extends IntegracionBase {

    static final String TOKEN_INTERNO = "token-interno-solo-para-desarrollo-local";
    static final String LECTURA = """
            {"lectura":{"esComprobante":true,"app":"YAPE","monto":45.00,"codigoOperacion":"04821937",
             "fechaHora":"2026-11-19T10:05","destinatario":"Complejo Deport* Alcanf*"},"validadoPor":"Canchita (IA)"}""";

    @Autowired
    ObjectMapper json;

    private long subirComprobante() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String cuerpo = mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "captura.png", "image/png", png(400, 700)))
                        .param("codigoReserva", CODIGO).param("dni", DNI).param("metodo", "YAPE")
                        .param("codigoOperacion", "04821937").param("comprobante", "{\"tipo\":\"BOLETA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("idPago").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions validarIa(long idPago, String cuerpo) throws Exception {
        return mvc.perform(post("/interno/pagos/{id}/validar-ia", idPago).header("X-Interno-Token", TOKEN_INTERNO)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    void validaComoElPanelConActorCanchitaYGuardaLaLectura() throws Exception {
        long idPago = subirComprobante();
        clearInvocations(publicador);

        validarIa(idPago, LECTURA).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.monto").value(45.00))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));

        ArgumentCaptor<PagoValidadoEvento> validado = ArgumentCaptor.forClass(PagoValidadoEvento.class);
        verify(publicador).publicar(eq(Eventos.PAGO_VALIDADO), validado.capture());
        assertThat(validado.getValue().validadoPor()).isEqualTo("Canchita (IA)");
        assertThat(validado.getValue().codigoReserva()).isEqualTo(CODIGO);
        verify(publicador).publicar(eq(Eventos.COMPROBANTE_EMITIDO), any(ComprobanteEmitidoEvento.class));
        ArgumentCaptor<AccionAuditadaEvento> auditoria = ArgumentCaptor.forClass(AccionAuditadaEvento.class);
        verify(publicador).publicar(eq(Eventos.AUDITORIA_ACCION), auditoria.capture());
        assertThat(auditoria.getValue().actor()).isEqualTo("Canchita (IA)");
        assertThat(auditoria.getValue().accion()).isEqualTo("VALIDAR_PAGO_IA");

        var fila = jdbc.queryForMap("SELECT estado_validacion, validado_por, id_administrador, lectura_ia FROM pago WHERE id_pago = ?", idPago);
        assertThat(fila.get("estado_validacion")).isEqualTo("VALIDADO");
        assertThat(fila.get("validado_por")).isEqualTo("Canchita (IA)");
        assertThat(fila.get("id_administrador")).isNull();
        assertThat(json.readTree((String) fila.get("lectura_ia")).get("codigoOperacion").asText()).isEqualTo("04821937");
    }

    @Test
    void esIdempotente() throws Exception {
        long idPago = subirComprobante();
        validarIa(idPago, LECTURA).andExpect(status().isOk());
        clearInvocations(publicador);

        validarIa(idPago, LECTURA).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        verify(publicador, never()).publicar(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comprobante", Integer.class)).isEqualTo(1);
    }

    @Test
    void unPagoRechazadoNoSeValidaYLaLecturaPuedeSerTexto() throws Exception {
        long idPago = subirComprobante();
        mvc.perform(post("/api/admin/pagos/{id}/rechazar", idPago).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"El monto no coincide con el adelanto.\"}"))
                .andExpect(status().isOk());
        validarIa(idPago, "{\"lectura\":\"S/ 45.00 a Complejo Deportivo Alcanfores\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_EN_REVISION"));
        verify(publicador, times(0)).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
    }

    @Test
    void exigeTokenInternoLecturaYPagoExistente() throws Exception {
        long idPago = subirComprobante();
        mvc.perform(post("/interno/pagos/{id}/validar-ia", idPago).contentType(MediaType.APPLICATION_JSON).content(LECTURA))
                .andExpect(status().isForbidden());
        mvc.perform(post("/interno/pagos/{id}/validar-ia", idPago).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(LECTURA))
                .andExpect(status().is4xxClientError());
        validarIa(idPago, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("lectura"));
        validarIa(999_999L, LECTURA).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM pago WHERE id_pago = ?", String.class, idPago))
                .isEqualTo("PENDIENTE");
    }
}
