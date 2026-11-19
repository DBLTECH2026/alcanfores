package pe.alcanfores.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto del comprobante en el cobro en caja (Yape, Plin o POS). */
class FotoCajaTest extends IntegracionBase {

    @Autowired
    private ObjectMapper json;

    private long cobrar(String metodo, String operacion) throws Exception {
        reservaDisponible(reserva(CODIGO, "PENDIENTE", "SIN_PAGO", "0"));
        String cuerpo = mvc.perform(post("/api/admin/pagos/caja").header("Authorization", tokenRecepcion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoReserva\":\"" + CODIGO + "\",\"tipoPago\":\"TOTAL\",\"metodo\":\"" + metodo
                                + "\",\"monto\":90.00" + (operacion == null ? "" : ",\"codigoOperacion\":\"" + operacion + "\"") + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(cuerpo);
        return n.get("idPago").asLong();
    }

    private MockMultipartFile foto(byte[] bytes) {
        return new MockMultipartFile("archivo", "yape.png", "image/png", bytes);
    }

    @Test
    void elCobroConYapeGuardaLaFotoYSePuedeVer() throws Exception {
        long id = cobrar("YAPE", "55443322");
        mvc.perform(multipart("/api/admin/pagos/{id}/archivo", id).file(foto(png(400, 700))).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/admin/pagos/{id}/archivo", id).header("Authorization", tokenRecepcion()))
                .andExpect(status().isOk());
    }

    @Test
    void enEfectivoNoSeAdjuntaFoto() throws Exception {
        long id = cobrar("EFECTIVO", null);
        mvc.perform(multipart("/api/admin/pagos/{id}/archivo", id).file(foto(png(400, 700))).header("Authorization", tokenRecepcion()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FOTO_NO_APLICA"));
    }

    @Test
    void unaImagenFalsaSeRechazaEnElCampoArchivo() throws Exception {
        long id = cobrar("PLIN", "66554433");
        mvc.perform(multipart("/api/admin/pagos/{id}/archivo", id).file(foto("no es imagen".getBytes()))
                        .header("Authorization", tokenRecepcion()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"));
    }

    @Test
    void sinTokenNoSeAdjunta() throws Exception {
        mvc.perform(multipart("/api/admin/pagos/{id}/archivo", 1L).file(foto(png(400, 700))))
                .andExpect(status().isUnauthorized());
    }
}
