package pe.alcanfores.pagos.servicio.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.TipoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** IGV incluido, importe en letras y textos del concepto que van en el comprobante. */
class ConceptoEIgvTest {

    @ParameterizedTest
    @CsvSource({"80.00,67.80,12.20", "45.00,38.14,6.86", "90.00,76.27,13.73", "0.50,0.42,0.08", "999.00,846.61,152.39"})
    void igvIncluidoCuadraAlCentimo(String total, String base, String igv) {
        BigDecimal t = new BigDecimal(total);
        assertThat(Dinero.baseImponible(t)).isEqualByComparingTo(base);
        assertThat(Dinero.igvIncluido(t)).isEqualByComparingTo(igv);
        assertThat(Dinero.baseImponible(t).add(Dinero.igvIncluido(t))).isEqualByComparingTo(total);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "80.00|OCHENTA CON 00/100 SOLES",
            "45.50|CUARENTA Y CINCO CON 50/100 SOLES",
            "21.00|VEINTIUNO CON 00/100 SOLES",
            "100.00|CIEN CON 00/100 SOLES",
            "1250.75|MIL DOSCIENTOS CINCUENTA CON 75/100 SOLES",
            "0.30|CERO CON 30/100 SOLES"})
    void importeEnLetras(String monto, String letras) {
        assertThat(Dinero.enLetras(new BigDecimal(monto))).isEqualTo(letras);
    }

    @Test
    void conceptoConCanchaDeporteDiaYHoras() {
        assertThat(ConceptoReserva.concepto("Cancha 2", "FUTBOL_6", LocalDate.of(2026, 11, 20), LocalTime.of(19, 0),
                LocalTime.of(20, 0), "ALC-7K3M9P"))
                .isEqualTo("Alquiler de Cancha 2 · Fútbol 6 · vie 20/11/2026 7:00–8:00 p. m.");
        // si el nombre ya trae el deporte no se repite
        assertThat(ConceptoReserva.concepto("Cancha 1 · Fútbol 5", "FUTBOL_5", LocalDate.of(2026, 11, 22),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "ALC-7K3M9P"))
                .isEqualTo("Alquiler de Cancha 1 · Fútbol 5 · dom 22/11/2026 9:00–10:00 a. m.");
        assertThat(ConceptoReserva.concepto(null, null, null, null, null, "ALC-7K3M9P"))
                .isEqualTo("Alquiler de cancha deportiva · Reserva ALC-7K3M9P");
    }

    @Test
    void conceptoConVariosTurnosUsaElHorarioTextoYCuantosSon() {
        assertThat(ConceptoReserva.concepto("Cancha 1 · Fútbol 5", "FUTBOL_5", LocalDate.of(2026, 11, 20),
                "9:00–10:00 a. m. y 7:00–8:00 p. m.", 2, "ALC-7K3M9P"))
                .isEqualTo("Alquiler de Cancha 1 · Fútbol 5 · vie 20/11/2026 9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)");
        // dos turnos seguidos: un solo tramo, pero igual se dice cuántos turnos son
        assertThat(ConceptoReserva.concepto("Losa", "MULTIUSO", LocalDate.of(2026, 11, 20), "7:00–9:00 p. m.", 2, "ALC-7K3M9P"))
                .isEqualTo("Alquiler de Losa · Multiusos · vie 20/11/2026 7:00–9:00 p. m. (2 turnos)");
        assertThat(ConceptoReserva.concepto("Losa", "MULTIUSO", LocalDate.of(2026, 11, 20), "7:00–8:00 p. m.", 1, "ALC-7K3M9P"))
                .doesNotContain("turnos");
        assertThat(ConceptoReserva.concepto("Losa", "MULTIUSO", LocalDate.of(2026, 11, 20), " ", 1, "ALC-7K3M9P"))
                .isEqualTo("Alquiler de cancha deportiva · Reserva ALC-7K3M9P");
        assertThat(ConceptoReserva.concepto("Losa", "MULTIUSO", LocalDate.of(2026, 11, 20), "7:00–8:00 p. m. ".repeat(20), 8,
                "ALC-7K3M9P")).hasSize(160);
    }

    @Test
    void conceptoDesdeElEventoDeReservasConYSinHorarioTexto() {
        ClienteContacto c = new ClienteContacto("70123321", "Diego", "Ramos", "987654321", null);
        DatosReserva v2 = new DatosReserva(10L, "ALC-7K3M9P", 1L, "Cancha 1", "FUTBOL_5", LocalDate.of(2026, 11, 21),
                LocalTime.of(9, 0), LocalTime.of(20, 0), new BigDecimal("160.00"), new BigDecimal("80.00"), BigDecimal.ZERO,
                c, "9:00–10:00 a. m. y 7:00–8:00 p. m.", 2);
        assertThat(ConceptoReserva.concepto(v2))
                .isEqualTo("Alquiler de Cancha 1 · Fútbol 5 · sáb 21/11/2026 9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)");
        // evento v1: sin horarioTexto ni cantidad de turnos → rango del primer al último turno y uno solo
        DatosReserva v1 = new DatosReserva(10L, "ALC-7K3M9P", 1L, "Cancha 1", "FUTBOL_5", LocalDate.of(2026, 11, 21),
                LocalTime.of(20, 0), LocalTime.of(21, 0), new BigDecimal("80.00"), new BigDecimal("40.00"), BigDecimal.ZERO,
                c, null, 0);
        assertThat(ConceptoReserva.concepto(v1)).isEqualTo("Alquiler de Cancha 1 · Fútbol 5 · sáb 21/11/2026 8:00–9:00 p. m.");
    }

    @Test
    void laInstantaneaConOtroHorarioConservaAlCliente() {
        InstantaneaReserva antes = new InstantaneaReserva("70123321", "Diego", "Ramos", "987654321", "d@correo.pe",
                "Alquiler de Cancha 2 · Fútbol 6 · vie 20/11/2026 7:00–8:00 p. m.", 50);
        InstantaneaReserva despues = antes.conHorario("Alquiler de Cancha 1 · Fútbol 5 · sáb 21/11/2026 8:00–9:00 p. m.", 40);
        assertThat(despues.getConcepto()).isEqualTo("Alquiler de Cancha 1 · Fútbol 5 · sáb 21/11/2026 8:00–9:00 p. m.");
        assertThat(despues.getPorcentajeAdelanto()).isEqualTo(40);
        assertThat(despues.contacto()).isEqualTo(antes.contacto());
        assertThat(antes.getConcepto()).contains("Cancha 2");
    }

    @Test
    void lineaDelComprobanteSegunTipoDePago() {
        InstantaneaReserva r = new InstantaneaReserva("70123321", "Diego", "Ramos", null, null, "Alquiler de Losa", 50);
        assertThat(ConceptoReserva.lineaComprobante(r, TipoPago.ADELANTO)).isEqualTo("Alquiler de Losa · Adelanto 50 %");
        assertThat(ConceptoReserva.lineaComprobante(r, TipoPago.TOTAL)).endsWith("· Pago total");
        assertThat(ConceptoReserva.lineaComprobante(r, TipoPago.SALDO)).endsWith("· Saldo");
        assertThat(ConceptoReserva.porcentaje(new BigDecimal("45.00"), new BigDecimal("90.00"))).isEqualTo(50);
        assertThat(ConceptoReserva.porcentaje(null, new BigDecimal("90.00"))).isNull();
        assertThat(ConceptoReserva.deporteTexto("VOLEY")).isEqualTo("Vóley");
        assertThat(ConceptoReserva.deporteTexto("BASQUET")).isEqualTo("Básquet");
        assertThat(ConceptoReserva.deporteTexto("OTRO")).isNull();
    }

    @Test
    void descripcionHtmlEscapaYProtegeElRangoHorario() {
        assertThat(GeneradorPdfComprobante.descripcionHtml("A <b> · 7:00 p. m.–8:00 p. m. · Adelanto"))
                .isEqualTo("A &lt;b&gt; · <span style=\"white-space: nowrap;\">7:00 p. m.–8:00 p. m.</span> · Adelanto");
        assertThat(GeneradorPdfComprobante.descripcionHtml("vie 9:00–10:00 a. m. y 11:00 a. m.–1:00 p. m. (2 turnos)"))
                .isEqualTo("vie <span style=\"white-space: nowrap;\">9:00–10:00 a. m.</span> y "
                        + "<span style=\"white-space: nowrap;\">11:00 a. m.–1:00 p. m.</span> (2 turnos)");
        assertThat(GeneradorPdfComprobante.motivoCatalogo09("01")).isEqualTo("Anulación de la operación");
        assertThat(GeneradorPdfComprobante.motivoCatalogo09("06")).isEqualTo("Devolución total");
    }
}
