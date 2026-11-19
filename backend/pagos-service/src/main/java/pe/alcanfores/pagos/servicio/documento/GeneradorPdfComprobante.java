package pe.alcanfores.pagos.servicio.documento;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoComprobante;
import pe.alcanfores.pagos.modelo.TipoComprobante;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Representación impresa (PDF A4) del comprobante simulado, al estilo de las de SUNAT: logo y datos del emisor,
 * recuadro RUC/tipo/serie-número, adquirente, ítem, totales, importe en letras, QR, valor resumen, marca de agua y pie.
 */
@Component
public class GeneradorPdfComprobante {

    private static final String FUENTES = "/documentos/fuentes/";
    private static final Pattern RANGO_HORAS = Pattern.compile(
            "\\d{1,2}:\\d{2}( [ap]\\. m\\.)?–\\d{1,2}:\\d{2} [ap]\\. m\\.");
    private final TemplateEngine plantillas;
    private final String logo;

    public GeneradorPdfComprobante() {
        ClassLoaderTemplateResolver resolutor = new ClassLoaderTemplateResolver();
        resolutor.setPrefix("plantillas/");
        resolutor.setSuffix(".html");
        resolutor.setTemplateMode(TemplateMode.HTML);
        resolutor.setCharacterEncoding("UTF-8");
        resolutor.setCacheable(true);
        this.plantillas = new TemplateEngine();
        this.plantillas.setTemplateResolver(resolutor);
        this.logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(leer("/documentos/logo-alcanfores-horizontal.png"));
    }

    public byte[] generar(DocumentoComprobante documento) {
        String html = plantillas.process("comprobante", contexto(documento));
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder pdf = new PdfRendererBuilder();
            pdf.useFastMode();
            fuente(pdf, "Inter-Regular.ttf", "Inter", 400);
            fuente(pdf, "Inter-SemiBold.ttf", "Inter", 600);
            fuente(pdf, "Inter-Bold.ttf", "Inter", 700);
            fuente(pdf, "BarlowSemiCondensed-SemiBold.ttf", "Barlow Semi Condensed", 600);
            fuente(pdf, "BarlowSemiCondensed-Bold.ttf", "Barlow Semi Condensed", 700);
            pdf.withHtmlContent(html, null);
            pdf.toStream(salida);
            pdf.run();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del comprobante", e);
        }
    }

    Context contexto(DocumentoComprobante documento) {
        Comprobante c = documento.comprobante();
        Comprobante ref = documento.referencia();
        boolean nota = c.getTipo() == TipoComprobante.NOTA_CREDITO;
        boolean factura = c.getTipo() == TipoComprobante.FACTURA
                || (nota && ref != null && ref.getTipo() == TipoComprobante.FACTURA);
        String tipoLargo = nota ? "nota de crédito electrónica" : factura ? "factura electrónica" : "boleta de venta electrónica";

        Context ctx = new Context(Tiempo.ES_PE);
        ctx.setVariable("tituloDocumento", tipoLargo.substring(0, 1).toUpperCase(Locale.ROOT) + tipoLargo.substring(1)
                + " " + c.serieNumero() + " (simulada)");
        ctx.setVariable("tituloTipo", tipoLargo.toUpperCase(Tiempo.ES_PE));
        ctx.setVariable("textoPie", "Representación impresa de la " + tipoLargo + " (simulada) · Proyecto académico UTP");
        ctx.setVariable("logo", logo);
        ctx.setVariable("emisorRuc", c.getEmisorRuc());
        ctx.setVariable("emisorRazonSocial", c.getEmisorRazonSocial());
        ctx.setVariable("emisorDireccion", c.getEmisorDireccion());
        ctx.setVariable("emisorContacto", "WhatsApp +51 914 995 187 · informes.alcanfores@gmail.com");
        ctx.setVariable("serieNumero", c.serieNumero());
        ctx.setVariable("clienteNombre", c.getClienteNombre());
        ctx.setVariable("clienteEtiquetaDoc", DatosFacturacion.DOC_RUC.equals(c.getClienteTipoDoc()) ? "RUC" : "DNI");
        ctx.setVariable("clienteNumDoc", c.getClienteNumDoc());
        ctx.setVariable("clienteDireccion", c.getClienteDireccion() == null || c.getClienteDireccion().isBlank()
                ? "—" : c.getClienteDireccion());
        ctx.setVariable("fechaEmision", c.getFechaEmision().format(Tiempo.FECHA_CORTA));
        ctx.setVariable("horaEmision", Tiempo.horaAmPm(c.getFechaEmision().toLocalTime()));
        ctx.setVariable("codigoReserva", c.getCodigoReserva());
        ctx.setVariable("formaPago", documento.formaPago());
        ctx.setVariable("descripcion", descripcionHtml(c.getDescripcion()));
        ctx.setVariable("valorUnitario", dos(c.getOpGravada()));
        ctx.setVariable("importe", dos(c.getOpGravada()));
        ctx.setVariable("opGravada", dos(c.getOpGravada()));
        ctx.setVariable("igv", dos(c.getIgv()));
        ctx.setVariable("total", dos(c.getTotal()));
        ctx.setVariable("montoLetras", c.getMontoLetras());
        ctx.setVariable("valorResumen", c.valorResumen());
        ctx.setVariable("estadoSunat", c.getEstadoSunat());
        ctx.setVariable("qr", GeneradorQr.dataUri(c.getCadenaQr(), 260));
        ctx.setVariable("esNota", nota && ref != null);
        if (nota && ref != null) {
            ctx.setVariable("referenciaTexto", (ref.getTipo() == TipoComprobante.FACTURA ? "Factura " : "Boleta ")
                    + ref.serieNumero() + " del " + ref.getFechaEmision().format(Tiempo.FECHA_CORTA));
            ctx.setVariable("tipoNota", c.getCodigoMotivo() + " · " + motivoCatalogo09(c.getCodigoMotivo()));
            ctx.setVariable("motivo", c.getMotivo());
        }
        ctx.setVariable("anulado", !nota && c.getEstado() == EstadoComprobante.ANULADO);
        ctx.setVariable("anuladoPor", " · se emitió una nota de crédito que lo anula.");
        return ctx;
    }

    /** Catálogo 09 de SUNAT (tipos de nota de crédito usados por el sistema). */
    public static String motivoCatalogo09(String codigo) {
        if (codigo == null) {
            return "";
        }
        return switch (codigo) {
            case "01" -> "Anulación de la operación";
            case "06" -> "Devolución total";
            default -> "Otros conceptos";
        };
    }

    /** Descripción escapada para HTML con cada rango de horas («7:00–8:00 p. m.», «11:00 a. m.–1:00 p. m.») protegido para que no se parta entre líneas. */
    static String descripcionHtml(String texto) {
        String escapado = HtmlUtils.htmlEscape(texto, "UTF-8");
        return RANGO_HORAS.matcher(escapado).replaceAll(r -> "<span style=\"white-space: nowrap;\">"
                + Matcher.quoteReplacement(r.group()) + "</span>");
    }

    private static String dos(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void fuente(PdfRendererBuilder pdf, String archivo, String familia, int peso) {
        pdf.useFont(() -> GeneradorPdfComprobante.class.getResourceAsStream(FUENTES + archivo), familia, peso,
                FontStyle.NORMAL, true);
    }

    private static byte[] leer(String recurso) {
        try (InputStream in = GeneradorPdfComprobante.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso " + recurso);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
