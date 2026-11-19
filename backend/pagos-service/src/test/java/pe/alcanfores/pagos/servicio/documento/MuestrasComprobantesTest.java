package pe.alcanfores.pagos.servicio.documento;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoComprobante;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.servicio.ConfiguracionComercio.Emisor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Genera las muestras de comprobantes (boleta, factura, nota de crédito y su XML) en {@code target/muestras}
 * y comprueba lo esencial: PDF válido, hash = SHA-256 del XML, QR con el formato de SUNAT.
 */
class MuestrasComprobantesTest {

    static final Path CARPETA = Path.of("target", "muestras");
    static final Emisor EMISOR = new Emisor("20000000001", "Los Alcanfores Sport E.I.R.L. (ficticia)",
            "Carretera Central km 11.5, Santa Clara, Ate, Lima");

    final GeneradorXmlUbl xml = new GeneradorXmlUbl();
    final ArmadorComprobante armador = new ArmadorComprobante(xml);
    final GeneradorPdfComprobante pdf = new GeneradorPdfComprobante();

    @BeforeAll
    static void carpeta() throws IOException {
        Files.createDirectories(CARPETA);
    }

    static Pago pago(TipoPago tipo, BigDecimal monto) {
        Pago p = new Pago();
        p.setId(12L);
        p.setCodigoReserva("ALC-7K3M9P");
        p.setMetodo(MetodoPago.YAPE);
        p.setCanal(CanalPago.COMPROBANTE);
        p.setTipoPago(tipo);
        p.setMonto(monto);
        p.setEstado(EstadoValidacion.VALIDADO);
        String concepto = ConceptoReserva.concepto("Cancha 2", "FUTBOL_6", LocalDate.of(2026, 11, 20),
                LocalTime.of(19, 0), LocalTime.of(20, 0), "ALC-7K3M9P");
        p.setReserva(new InstantaneaReserva("70123321", "Diego Alonso", "Ramos Quispe", "987654321",
                "diego.ramos@correo.pe", concepto, 50));
        return p;
    }

    static DatosFacturacion boleta() {
        DatosFacturacion df = new DatosFacturacion();
        df.setTipo(TipoComprobante.BOLETA);
        df.setTipoDoc(DatosFacturacion.DOC_DNI);
        df.setNumDoc("70123321");
        df.setNombre("Diego Alonso Ramos Quispe");
        return df;
    }

    static DatosFacturacion factura() {
        DatosFacturacion df = new DatosFacturacion();
        df.setTipo(TipoComprobante.FACTURA);
        df.setTipoDoc(DatosFacturacion.DOC_RUC);
        df.setNumDoc("20601234565");
        df.setNombre("Inversiones Deportivas Santa Clara S.A.C. (ficticia)");
        df.setDireccion("Av. Nicolás Ayllón 1234, Ate, Lima");
        return df;
    }

    @Test
    void boletaDeAdelanto() throws IOException {
        Comprobante c = armador.armar(pago(TipoPago.ADELANTO, new BigDecimal("45.00")), boleta(), EMISOR, "B001", 12,
                LocalDateTime.of(2026, 11, 18, 19, 5));
        String contenidoXml = armador.xml(c, null);

        assertThat(c.getDescripcion())
                .isEqualTo("Alquiler de Cancha 2 · Fútbol 6 · vie 20/11/2026 7:00 p. m.–8:00 p. m. · Adelanto 50 %");
        assertThat(c.getOpGravada()).isEqualByComparingTo("38.14");
        assertThat(c.getIgv()).isEqualByComparingTo("6.86");
        assertThat(c.getMontoLetras()).isEqualTo("CUARENTA Y CINCO CON 00/100 SOLES");
        assertThat(c.getHash()).isEqualTo(HuellaComprobante.sha256Base64(contenidoXml)).hasSize(44);
        assertThat(c.valorResumen()).hasSize(28);
        assertThat(c.getCadenaQr()).isEqualTo("20000000001|03|B001|00000012|6.86|45.00|2026-11-18|1|70123321|" + c.valorResumen());
        assertThat(contenidoXml).contains("<Invoice", "<cbc:ID>B001-00000012</cbc:ID>", "DOCUMENTO SIMULADO",
                "unitCode=\"ZZ\"", "<cbc:PayableAmount currencyID=\"PEN\">45.00</cbc:PayableAmount>");

        guardar("boleta-B001-00000012", c, null, contenidoXml);
    }

    @Test
    void facturaPagoTotalYSuNotaDeCredito() throws IOException {
        Comprobante f = armador.armar(pago(TipoPago.TOTAL, new BigDecimal("90.00")), factura(), EMISOR, "F001", 3,
                LocalDateTime.of(2026, 11, 18, 20, 12));
        f.setId(3L);
        String xmlFactura = armador.xml(f, null);
        assertThat(f.getCadenaQr()).startsWith("20000000001|01|F001|00000003|13.73|90.00|2026-11-18|6|20601234565|");
        guardar("factura-F001-00000003", f, null, xmlFactura);

        Comprobante nc = armador.armarNotaCredito(f, ArmadorComprobante.serieNotaPara(f), 1,
                LocalDateTime.of(2026, 11, 19, 9, 30), "06", "Devolución por cancelación de la reserva con más de 24 horas de anticipación.");
        f.setEstado(EstadoComprobante.ANULADO);
        String xmlNota = armador.xml(nc, f);
        assertThat(nc.serieNumero()).isEqualTo("FC01-00000001");
        assertThat(xmlNota).contains("<CreditNote", "<cbc:ReferenceID>F001-00000003</cbc:ReferenceID>",
                "<cbc:ResponseCode>06</cbc:ResponseCode>", "<cbc:DocumentTypeCode>01</cbc:DocumentTypeCode>");
        assertThat(nc.getHash()).isEqualTo(HuellaComprobante.sha256Base64(xmlNota));
        guardar("nota-credito-FC01-00000001", nc, f, xmlNota);
    }

    private void guardar(String nombre, Comprobante c, Comprobante ref, String contenidoXml) throws IOException {
        byte[] bytes = pdf.generar(new DocumentoComprobante(c, ref, "Contado · Yape"));
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Files.write(CARPETA.resolve(nombre + ".pdf"), bytes);
        Files.writeString(CARPETA.resolve(nombre + ".xml"), contenidoXml, StandardCharsets.UTF_8);
    }
}
