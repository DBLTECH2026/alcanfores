package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pendientes del back encontrados al integrar el front: la factura pedida no se pierde al reenviar, enlaces completos en
 * el sondeo del intento, {@code OPERACION_REPETIDA} con campo y migración del RUC del emisor ficticio.
 */
class ArreglosPagosTest extends IntegracionBase {

    private static final String FACTURA = "{\"tipo\":\"FACTURA\",\"ruc\":\"20131312955\",\"razonSocial\":\"Club Deportivo UTP S.A.C.\","
            + "\"direccionFiscal\":\"Av. Nicolás Ayllón 123, Ate\"}";

    @Autowired
    ObjectMapper json;

    private ResultActions subir(String codigo, String operacion, String comprobante) throws Exception {
        var peticion = multipart("/api/pagos/comprobante")
                .file(new MockMultipartFile("archivo", "captura.png", "image/png", png(400, 700)))
                .param("codigoReserva", codigo).param("dni", DNI).param("metodo", "YAPE").param("codigoOperacion", operacion);
        if (comprobante != null) {
            peticion.param("comprobante", comprobante);
        }
        return mvc.perform(peticion);
    }

    private long idPago(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("idPago").asLong();
    }

    private void rechazar(long idPago) throws Exception {
        mvc.perform(post("/api/admin/pagos/{id}/rechazar", idPago).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"La captura no muestra el número de operación.\"}"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 1: la factura pedida al reservar no se pierde

    @Test
    void elReenvioSinComprobanteRepiteLaFacturaPedida() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        long primero = idPago(subir(CODIGO, "04821937", FACTURA));
        rechazar(primero);

        reservaDisponible(reserva(CODIGO, "PENDIENTE", "OBSERVADO", "0"));
        long segundo = idPago(subir(CODIGO, "04821938", null));
        mvc.perform(post("/api/admin/pagos/{id}/validar", segundo).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("F001-00000001"));
        assertThat(jdbc.queryForMap("SELECT cliente_num_doc, cliente_nombre FROM comprobante"))
                .containsEntry("cliente_num_doc", "20131312955").containsEntry("cliente_nombre", "Club Deportivo UTP S.A.C.");

        // La consulta del cliente dice qué comprobante pidió en cada envío (para prellenar el reenvío).
        mvc.perform(get("/api/pagos/reserva/{codigo}", CODIGO).param("dni", DNI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].facturacion.tipo").value("FACTURA"))
                .andExpect(jsonPath("$[0].facturacion.ruc").value("20131312955"))
                .andExpect(jsonPath("$[1].facturacion.tipo").value("FACTURA"))
                .andExpect(jsonPath("$[1].facturacion.razonSocial").value("Club Deportivo UTP S.A.C."))
                .andExpect(jsonPath("$[1].facturacion.direccionFiscal").value("Av. Nicolás Ayllón 123, Ate"));
    }

    @Test
    void unaBoletaExplicitaSiCambiaElComprobante() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        rechazar(idPago(subir(CODIGO, "04821937", FACTURA)));
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "OBSERVADO", "0"));
        long segundo = idPago(subir(CODIGO, "04821938", "{\"tipo\":\"BOLETA\"}"));
        mvc.perform(post("/api/admin/pagos/{id}/validar", segundo).header("Authorization", tokenRecepcion()))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("B001-00000001"));
        mvc.perform(get("/api/pagos/reserva/{codigo}", CODIGO).param("dni", DNI))
                .andExpect(jsonPath("$[1].facturacion.tipo").value("BOLETA"))
                .andExpect(jsonPath("$[1].facturacion.ruc").doesNotExist());
    }

    @Test
    void elPagoEnLineaDesdeMiReservaTambienRepiteLaFactura() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        rechazar(idPago(subir(CODIGO, "04821937", FACTURA)));
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "OBSERVADO", "0"));
        String ref = json.readTree(mvc.perform(post("/api/pagos/pasarela/intentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"dni\":\"" + DNI + "\",\"tipoPago\":\"ADELANTO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("referencia").asText();
        String tarjeta = json.writeValueAsString(Map.of("numero", "4111 1111 1111 1111", "titular", "Diego Ramos",
                "mesVencimiento", 12, "anioVencimiento", 30, "cvv", "123", "correo", "diego.ramos@correo.pe"));
        mvc.perform(post("/api/pasarela/intentos/{ref}/tarjeta", ref).contentType(MediaType.APPLICATION_JSON).content(tarjeta))
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        // 5: el sondeo del intento trae los enlaces completos (con &dni=), igual que /api/pagos/reserva/{codigo}.
        mvc.perform(get("/api/pagos/intentos/{ref}/estado", ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("F001-00000001"))
                .andExpect(jsonPath("$.comprobante.urlPdf")
                        .value("/api/comprobantes/F001-00000001.pdf?codigo=" + CODIGO + "&dni=" + DNI))
                .andExpect(jsonPath("$.comprobante.urlXml")
                        .value("/api/comprobantes/F001-00000001.xml?codigo=" + CODIGO + "&dni=" + DNI));
        mvc.perform(get("/api/comprobantes/F001-00000001.pdf").param("codigo", CODIGO).param("dni", DNI))
                .andExpect(status().isOk());
    }

    @Test
    void elCobroDelSaldoEnCajaRepiteLaFacturaDelAdelanto() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        long adelanto = idPago(subir(CODIGO, "04821937", FACTURA));
        mvc.perform(post("/api/admin/pagos/{id}/validar", adelanto).header("Authorization", tokenRecepcion()))
                .andExpect(jsonPath("$.comprobante.serieNumero").value("F001-00000001"));
        reservaDisponible(reserva(CODIGO, "CONFIRMADA", "ADELANTO_PAGADO", "45.00"));
        mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"SALDO\",\"metodo\":\"EFECTIVO\",\"monto\":45.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comprobante.serieNumero").value("F001-00000002"));
    }

    // ---------------------------------------------------------------- 9: OPERACION_REPETIDA con campo

    @Test
    void operacionRepetidaMarcaElCampoCodigoOperacion() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        idPago(subir(CODIGO, "28027508", null));
        reservaDisponible(reserva("ALC-QW3RT7", "PENDIENTE", "SIN_PAGO", "0"));
        subir("ALC-QW3RT7", "28027508", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OPERACION_REPETIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("codigoOperacion"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(containsString("ya fue registrado")))
                .andExpect(jsonPath("$.mensaje").value(containsString("Revisa tu constancia de Yape")));
    }

    // ---------------------------------------------------------------- 7: RUC del emisor en comprobantes ya emitidos

    @Test
    void v2PasaLosComprobantesDelRucViejoAlFicticio() throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        long id = idPago(subir(CODIGO, "04821937", null));
        mvc.perform(post("/api/admin/pagos/{id}/validar", id).header("Authorization", tokenRecepcion())).andExpect(status().isOk());
        String qr = jdbc.queryForObject("SELECT cadena_qr FROM comprobante", String.class);
        assertThat(qr).startsWith("20000000001|03|B001|");
        // Así quedaron los comprobantes emitidos antes del cambio.
        jdbc.update("UPDATE comprobante SET emisor_ruc = '20612345679', cadena_qr = ?", "20612345679" + qr.substring(11));

        String sql = new ClassPathResource("db/migration/V2__ruc_emisor_ficticio.sql").getContentAsString(StandardCharsets.UTF_8);
        String sinComentarios = sql.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
        Arrays.stream(sinComentarios.split(";")).map(String::strip).filter(s -> !s.isEmpty()).forEach(jdbc::update);

        assertThat(jdbc.queryForMap("SELECT emisor_ruc, cadena_qr FROM comprobante"))
                .containsEntry("emisor_ruc", "20000000001").containsEntry("cadena_qr", qr);
        mvc.perform(get("/api/comprobantes/B001-00000001.xml").param("codigo", CODIGO).param("dni", DNI))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("<cbc:ID schemeID=\"6\">20000000001</cbc:ID>")));
    }
}
