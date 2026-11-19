package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprobante Yape/Plin → validar → boleta/factura → eventos; rechazo, caja, anulación, descargas y permisos. */
class FlujoComprobanteYapeTest extends IntegracionBase {

    static final String DOS_TURNOS = "ALC-DSTRNS";

    @Autowired
    ObjectMapper json;

    private ResultActions subir(byte[] imagen, String comprobante) throws Exception {
        return mvc.perform(multipart("/api/pagos/comprobante")
                .file(new MockMultipartFile("archivo", "captura.png", "image/png", imagen))
                .param("codigoReserva", CODIGO).param("dni", DNI).param("metodo", "YAPE")
                .param("codigoOperacion", "04821937").param("comprobante", comprobante));
    }

    private long subirValido(String comprobante) throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String cuerpo = subir(png(400, 700), comprobante).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("idPago").asLong();
    }

    @Test
    void validaCadaCampoDelFormulario() throws Exception {
        mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", "no soy una imagen".getBytes()))
                        .param("codigoReserva", "ALC-0000").param("dni", "123").param("metodo", "BITCOIN")
                        .param("codigoOperacion", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "archivo", "codigoOperacion", "codigoReserva", "dni", "metodo")))
                .andExpect(jsonPath("$.errores[?(@.campo=='archivo')].mensaje").value(hasItem("Sube una imagen JPG o PNG de hasta 5 MB.")))
                .andExpect(jsonPath("$.errores[?(@.campo=='dni')].mensaje").value(hasItem("El DNI debe tener 8 dígitos.")));
    }

    @Test
    void imagenFalsaConExtensionPngSeRechazaPorBytesMagicos() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        byte[] falsa = "GIF89a esto no es un PNG aunque se llame .png".getBytes(StandardCharsets.US_ASCII);
        subir(falsa, "{\"tipo\":\"BOLETA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"));
        subir(png(120, 120), "{\"tipo\":\"BOLETA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("200 px")));
    }

    @Test
    void facturaConRucInvalidoODeOtroTipoNoSeAcepta() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        subir(png(400, 700), "{\"tipo\":\"FACTURA\",\"ruc\":\"20000000002\",\"razonSocial\":\"Mi Empresa SAC\",\"direccionFiscal\":\"Av. Lima 123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("comprobante.ruc"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El RUC no es válido."));
        subir(png(400, 700), "{\"tipo\":\"FACTURA\",\"ruc\":\"20000000001\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "comprobante.direccionFiscal", "comprobante.razonSocial")))
                .andExpect(jsonPath("$.errores[?(@.campo=='comprobante.razonSocial')].mensaje")
                        .value(hasItem("Para factura necesitas un RUC válido y la razón social.")));
    }

    @Test
    void elDniDebeCoincidirConLaReserva() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        mvc.perform(multipart("/api/pagos/comprobante")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", png(400, 700)))
                        .param("codigoReserva", CODIGO).param("dni", "11111111").param("metodo", "PLIN")
                        .param("codigoOperacion", "123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No encontramos una reserva con ese código y DNI."));
    }

    @Test
    void reservasCaidoRespondeServicioNoDisponible() throws Exception {
        when(reservas.buscar(CODIGO)).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                "El servicio de reservas no responde. Intenta en unos segundos."));
        subir(png(400, 700), "").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    void flujoCompletoSubirValidarYDescargarBoleta() throws Exception {
        long idPago = subirValido("{\"tipo\":\"BOLETA\"}");
        verify(publicador).publicar(eq(Eventos.PAGO_REGISTRADO), any(PagoRegistradoEvento.class));

        // un segundo comprobante para la misma reserva queda bloqueado mientras el primero está en revisión
        subir(png(400, 700), "").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EN_REVISION"));

        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/pagos").header("Authorization", tokenRecepcion()).param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.conteos.PENDIENTE").value(1));
        mvc.perform(get("/api/admin/pagos/{id}/archivo", idPago).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDADO"))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        verify(publicador).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
        verify(publicador).publicar(eq(Eventos.COMPROBANTE_EMITIDO), any(ComprobanteEmitidoEvento.class));
        verify(publicador).publicar(eq(Eventos.AUDITORIA_ACCION), any(AccionAuditadaEvento.class));

        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenAdmin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_YA_PROCESADO"));

        JsonNode detalle = json.readTree(mvc.perform(get("/api/admin/pagos/{id}", idPago).header("Authorization", tokenAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detalle.get("validadoPor").asText()).isEqualTo("mtorres");
        assertThat(detalle.get("concepto").asText()).startsWith("Alquiler de Cancha 2 · Fútbol 6 · ");

        Integer total = jdbc.queryForObject("SELECT CAST(total * 100 AS SIGNED) FROM comprobante", Integer.class);
        assertThat(total).isEqualTo(4500);

        mvc.perform(get("/api/pagos/reserva/{codigo}", CODIGO).param("dni", DNI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("VALIDADO"))
                .andExpect(jsonPath("$[0].comprobante.urlPdf").value("/api/comprobantes/B001-00000001.pdf?codigo=" + CODIGO + "&dni=" + DNI));
        mvc.perform(get("/api/pagos/reserva/{codigo}", CODIGO).param("dni", "11111111")).andExpect(status().isNotFound());

        mvc.perform(get("/api/comprobantes/B001-00000001.pdf").param("codigo", CODIGO).param("dni", DNI))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/comprobantes/B001-00000001.xml").param("codigo", CODIGO).param("dni", DNI))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("<cbc:ID>B001-00000001</cbc:ID>")));
        mvc.perform(get("/api/comprobantes/B001-00000001.pdf").param("codigo", CODIGO).param("dni", "11111111"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/comprobantes/B001-00000001.pdf").param("codigo", CODIGO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("dni"));
    }

    @Test
    void reservaDeDosTurnosNoSeguidosDescribeAmbosEnElComprobante() throws Exception {
        reservaDisponible(reservaDosTurnos(DOS_TURNOS));
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + DOS_TURNOS + "\",\"tipoPago\":\"TOTAL\",\"metodo\":\"EFECTIVO\",\"monto\":160.00}"))
                .andExpect(status().isCreated());
        String descripcion = jdbc.queryForObject("SELECT descripcion FROM comprobante", String.class);
        assertThat(descripcion).startsWith("Alquiler de Cancha 2 · Fútbol 6 · ")
                .endsWith(" 9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos) · Pago total");
        assertThat(jdbc.queryForObject("SELECT CAST(total * 100 AS SIGNED) FROM comprobante", Integer.class)).isEqualTo(16000);
        String serie = jdbc.queryForObject("SELECT CONCAT(serie, '-', LPAD(correlativo, 8, '0')) FROM comprobante", String.class);
        byte[] xml = mvc.perform(get("/api/comprobantes/" + serie + ".xml").param("codigo", DOS_TURNOS).param("dni", DNI))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xml, StandardCharsets.UTF_8)).contains("9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)");
        mvc.perform(get("/api/comprobantes/" + serie + ".pdf").param("codigo", DOS_TURNOS).param("dni", DNI))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void facturaValidaSeEmiteEnLaSerieF001() throws Exception {
        long idPago = subirValido("{\"tipo\":\"FACTURA\",\"ruc\":\"20000000001\",\"razonSocial\":\"Mi Empresa S.A.C.\",\"direccionFiscal\":\"Av. Lima 123, Ate\"}");
        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("F001-00000001"));
        assertThat(jdbc.queryForObject("SELECT cliente_num_doc FROM comprobante", String.class)).isEqualTo("20000000001");
    }

    @Test
    void rechazoConObservacion() throws Exception {
        long idPago = subirValido("");
        mvc.perform(post("/api/admin/pagos/{id}/rechazar", idPago).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"corto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Cuéntanos el motivo (mínimo 10 caracteres)."));
        mvc.perform(post("/api/admin/pagos/{id}/rechazar", idPago).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"La captura no muestra el número de operación.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
        verify(publicador).publicar(eq(Eventos.PAGO_RECHAZADO), any(PagoRechazadoEvento.class));
        verify(publicador, never()).publicar(eq(Eventos.COMPROBANTE_EMITIDO), any());
    }

    @Test
    void cajaNoPermiteCobrarMasQueElSaldo() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String cuerpo = "{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"ADELANTO\",\"metodo\":\"EFECTIVO\",\"monto\":%s}";
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted("120.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("monto"))
                .andExpect(jsonPath("$.mensaje").value("El monto no puede superar el saldo de S/ 90.00."));
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted("-5")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted("45.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        // con el adelanto cobrado, el siguiente cobro es el saldo (S/ 45.00) y no puede excederlo
        String saldo = "{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"SALDO\",\"metodo\":\"YAPE\",\"codigoOperacion\":\"99887766\",\"monto\":%s}";
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(saldo.formatted("50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El monto no puede superar el saldo de S/ 45.00."));
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(saldo.formatted("45.00")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(saldo.formatted("1.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_SALDO"));
    }

    @Test
    void cajaInternaConLaReservaEnElCuerpo() throws Exception {
        String cuerpo = """
                {"codigoReserva":"%s","tipoPago":"TOTAL","metodo":"EFECTIVO","monto":90.00,
                 "comprobante":{"tipo":"BOLETA"},"registradoPor":2,"registradoPorUsuario":"mtorres",
                 "reserva":{"idReserva":10,"codigoReserva":"%s","idCancha":2,"nombreCancha":"Cancha 2","deporte":"FUTBOL_6",
                  "fecha":"2026-12-01","horaInicio":"19:00","horaFin":"20:00","montoTotal":90.00,"montoAdelanto":45.00,
                  "montoPagado":0,"cliente":{"dni":"%s","nombres":"Diego Alonso","apellidos":"Ramos Quispe",
                  "telefono":"987654321","correo":"diego.ramos@correo.pe"}}}
                """.formatted(CODIGO, CODIGO, DNI);
        mvc.perform(post("/interno/pagos/caja").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/interno/pagos/caja").header("X-Interno-Token", "token-interno-solo-para-desarrollo-local")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("VALIDADO"));
        verify(publicador).publicar(eq(Eventos.PAGO_VALIDADO), any(PagoValidadoEvento.class));
        assertThat(jdbc.queryForMap("SELECT validado_por, id_administrador FROM pago"))
                .containsEntry("validado_por", "mtorres").containsEntry("id_administrador", 2L);
        long idComprobante = jdbc.queryForObject("SELECT id_comprobante FROM comprobante", Long.class);
        mvc.perform(get("/interno/comprobantes/{id}/pdf", idComprobante).header("X-Interno-Token", "token-interno-solo-para-desarrollo-local"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void anularEsSoloDeAdminYEmiteNotaDeCredito() throws Exception {
        long idPago = subirValido("");
        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenAdmin())).andExpect(status().isOk());
        long id = jdbc.queryForObject("SELECT id_comprobante FROM comprobante", Long.class);
        String motivo = "{\"motivo\":\"El cliente pidió factura en lugar de boleta.\"}";

        mvc.perform(post("/api/admin/comprobantes/{id}/anular", id).header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mvc.perform(post("/api/admin/comprobantes/{id}/anular", id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/comprobantes/{id}/anular", id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serieNumero").value("BC01-00000001"))
                .andExpect(jsonPath("$.serieNumeroReferencia").value("B001-00000001"))
                .andExpect(jsonPath("$.tipo").value("NOTA_CREDITO"));
        mvc.perform(post("/api/admin/comprobantes/{id}/anular", id).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COMPROBANTE_ANULADO"));

        mvc.perform(get("/api/admin/comprobantes").header("Authorization", tokenRecepcion()).param("tipo", "NOTA_CREDITO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/admin/comprobantes").header("Authorization", tokenRecepcion()).param("q", "B001-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].estado").value("ANULADO"));
        mvc.perform(get("/api/admin/comprobantes").header("Authorization", tokenRecepcion()).param("tipo", "TICKET"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/comprobantes").header("Authorization", tokenRecepcion()).param("sort", "inexistente,desc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("sort"));
    }

    @Test
    void reporteDeIngresosSoloParaAdmin() throws Exception {
        long idPago = subirValido("");
        mvc.perform(post("/api/admin/pagos/{id}/validar", idPago).header("Authorization", tokenAdmin())).andExpect(status().isOk());

        mvc.perform(get("/api/admin/reportes/ingresos").header("Authorization", tokenRecepcion()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reportes/ingresos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reportes/ingresos").header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(45.00))
                .andExpect(jsonPath("$.porMetodo[0].clave").value("YAPE"))
                .andExpect(jsonPath("$.porTipoComprobante[0].clave").value("BOLETA"))
                .andExpect(jsonPath("$.porDia", hasSize(30)));
        mvc.perform(get("/api/admin/reportes/ingresos").header("Authorization", tokenAdmin())
                        .param("desde", "2026-12-01").param("hasta", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenInvalidoOAlteradoEs401() throws Exception {
        mvc.perform(get("/api/admin/pagos").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
