package pe.alcanfores.reservas;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Constancia PDF (pública, del panel e interna) y reportes de reservas en JSON y Excel. */
class ConstanciaYReportesTest extends PruebaIntegracion {

    @Autowired
    private PagoEventosServicio pagoEventos;

    @Test
    void laConstanciaEsUnPdfReal() throws Exception {
        String codigo = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").get("codigoReserva").asText();
        byte[] pdf = mvc.perform(get("/api/reservas/" + codigo + "/constancia.pdf").param("dni", "70123321"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(codigo)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(10_000);

        mvc.perform(get("/api/reservas/" + codigo + "/constancia.pdf").param("dni", "11111111"))
                .andExpect(status().isNotFound());
    }

    @Test
    void laConstanciaSeGeneraTambienConfirmadaCanceladaYPorLasRutasInternas() throws Exception {
        String codigo = reservarWeb(turno(2, HOY.plusDays(3), 20).getId(), "70123321").get("codigoReserva").asText();
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, codigo, "YAPE", "COMPROBANTE",
                new BigDecimal("45.00"), "ADELANTO", "mtorres"));
        byte[] confirmada = mvc.perform(get("/api/admin/reservas/" + codigo + "/constancia.pdf").header("Authorization", token("RECEPCION")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(confirmada, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        mvc.perform(post("/api/reservas/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123321\",\"motivo\":\"Cambio de planes del equipo\"}")).andExpect(status().isOk());
        byte[] cancelada = mvc.perform(get("/interno/reservas/" + codigo + "/constancia.pdf").header("X-Interno-Token", TOKEN_INTERNO))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(cancelada, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void elReporteResumeTotalesOcupacionCancelacionesYTopClientes() throws Exception {
        String a = reservarWeb(turno(1, HOY, 19).getId(), "70123321").get("codigoReserva").asText();
        pagoEventos.alValidarPago(new PagoValidadoEvento(UUID.randomUUID(), AHORA, 1L, a, "YAPE", "COMPROBANTE",
                new BigDecimal("40.00"), "ADELANTO", "mtorres"));
        String b = reservarWeb(turno(1, HOY, 20).getId(), "70123322").get("codigoReserva").asText();
        mvc.perform(post("/api/reservas/" + b + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123322\",\"motivo\":\"No completamos el equipo\"}")).andExpect(status().isOk());
        reservarWeb(turno(2, HOY.plusDays(1), 21).getId(), "70123321");

        mvc.perform(get("/api/admin/reportes/reservas").header("Authorization", token("ADMIN"))
                        .param("desde", HOY.toString()).param("hasta", HOY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.registradas").value(3))
                .andExpect(jsonPath("$.totales.confirmadas").value(1))
                .andExpect(jsonPath("$.totales.canceladas").value(1))
                .andExpect(jsonPath("$.totales.pendientes").value(1))
                .andExpect(jsonPath("$.totales.ocupacionPct").value(1.7))
                .andExpect(jsonPath("$.porCancha[0].reservas").value(1))
                .andExpect(jsonPath("$.porDia.length()").value(2))
                .andExpect(jsonPath("$.porDia[0].registradas").value(2))
                .andExpect(jsonPath("$.porDia[0].canceladas").value(1))
                .andExpect(jsonPath("$.porHora.length()").value(15))
                .andExpect(jsonPath("$.porHora[11].hora").value("19:00"))
                .andExpect(jsonPath("$.porHora[11].reservas").value(1))
                .andExpect(jsonPath("$.porOrigen[0].origen").value("WEB"))
                .andExpect(jsonPath("$.porOrigen[0].reservas").value(3))
                .andExpect(jsonPath("$.cancelaciones.porActor.CLIENTE").value(1))
                .andExpect(jsonPath("$.cancelaciones.motivosFrecuentes[0].motivo").value("No completamos el equipo"))
                .andExpect(jsonPath("$.topClientes[0].dni").value("70123321"))
                .andExpect(jsonPath("$.topClientes[0].reservas").value(2));

        mvc.perform(get("/api/admin/reportes/reservas").header("Authorization", token("ADMIN"))
                        .param("desde", HOY.toString()).param("hasta", HOY.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elExcelTraeLasCuatroHojasConDatos() throws Exception {
        reservarWeb(turno(1, HOY, 19).getId(), "70123321");
        byte[] xlsx = mvc.perform(get("/api/admin/reportes/reservas.xlsx").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(4);
            assertThat(libro.getSheetName(0)).isEqualTo("Resumen");
            assertThat(libro.getSheetName(1)).isEqualTo("Por cancha");
            assertThat(libro.getSheetName(2)).isEqualTo("Por día");
            assertThat(libro.getSheetName(3)).isEqualTo("Reservas");
            assertThat(libro.getSheet("Reservas").getRow(2).getCell(0).getStringCellValue()).isEqualTo("Código");
            assertThat(libro.getSheet("Reservas").getRow(3).getCell(5).getStringCellValue()).isEqualTo("70123321");
            assertThat(libro.getSheet("Resumen").getRow(4).getCell(1).getNumericCellValue()).isEqualTo(1.0);
        }
    }
}
