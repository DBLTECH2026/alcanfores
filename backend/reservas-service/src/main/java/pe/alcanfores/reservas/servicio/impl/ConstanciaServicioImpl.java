package pe.alcanfores.reservas.servicio.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ConstanciaServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConstanciaServicioImpl implements ConstanciaServicio {

    private static final String RAIZ = "constancia/";
    private static final int ANCHO_LOGO = 760;

    private final TemplateEngine motor;
    private final ComplejoServicio complejo;
    private final AlcanforesPropiedades propiedades;
    private final String logo;

    public ConstanciaServicioImpl(ComplejoServicio complejo, AlcanforesPropiedades propiedades) {
        this.complejo = complejo;
        this.propiedades = propiedades;
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix("plantillas/");
        resolvedor.setSuffix(".html");
        resolvedor.setTemplateMode(TemplateMode.HTML);
        resolvedor.setCharacterEncoding("UTF-8");
        resolvedor.setCacheable(true);
        this.motor = new TemplateEngine();
        this.motor.setTemplateResolver(resolvedor);
        this.logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(logoReducido());
    }

    @Override
    public byte[] generar(Reserva r, Cancelacion cancelacion) {
        Context ctx = new Context(Tiempo.ES_PE);
        ctx.setVariables(modelo(r, cancelacion));
        String html = motor.process("constancia", ctx);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder pdf = new PdfRendererBuilder();
            pdf.useFastMode();
            pdf.withHtmlContent(html, null);
            fuente(pdf, "BarlowSC", 600, "BarlowSemiCondensed-SemiBold.ttf");
            fuente(pdf, "BarlowSC", 700, "BarlowSemiCondensed-Bold.ttf");
            fuente(pdf, "Inter", 400, "inter-400.ttf");
            fuente(pdf, "Inter", 600, "inter-600.ttf");
            fuente(pdf, "Inter", 700, "inter-700.ttf");
            pdf.toStream(salida);
            pdf.run();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la constancia " + r.getCodigoReserva(), e);
        }
    }

    private Map<String, Object> modelo(Reserva r, Cancelacion c) {
        ParametrosComplejo p = complejo.parametros();
        Turno t = r.getTurno();
        Cliente cl = r.getCliente();
        Map<String, Object> m = new HashMap<>();
        m.put("logo", logo);
        m.put("qr", qr(propiedades.getFrontUrl() + "/mi-reserva?codigo=" + r.getCodigoReserva()));
        m.put("codigo", r.getCodigoReserva());
        m.put("estado", ReglasReserva.estadoTexto(r));
        m.put("claseEstado", claseEstado(r));
        String deporte = ReglasReserva.deporteTexto(t.getDeporte());
        m.put("canchaDeporte", t.getNombreCancha().contains(deporte) ? t.getNombreCancha() : t.getNombreCancha() + " · " + deporte);
        m.put("fecha", mayuscula(Tiempo.fechaLarga(t.getFecha())));
        int cantidad = r.cantidadTurnos();
        m.put("hora", r.horarioTexto() + (cantidad > 1 ? " (" + cantidad + " turnos)" : ""));
        long franjas = r.turnosOrdenados().stream().map(Turno::getFranja).distinct().count();
        m.put("franja", franjas > 1 ? "Tarifa día y noche" : t.getFranja().name().equals("NOCHE") ? "Tarifa noche" : "Tarifa día");
        m.put("cliente", cl.nombreCompleto());
        m.put("dni", Documentos.enmascararDni(cl.getDni()));
        m.put("telefono", cl.getTelefono().replaceAll("(\\d{3})(\\d{3})(\\d{3})", "$1 $2 $3"));
        m.put("correo", cl.getCorreo());
        m.put("registrada", r.getFechaRegistro().format(Tiempo.FECHA_HORA));
        m.put("origen", switch (r.getOrigen()) {
            case WEB -> "Web";
            case PRESENCIAL -> "En el complejo";
            case TELEFONICA -> "Por teléfono";
        });
        m.put("total", Dinero.formato(r.getMontoTotal()));
        m.put("adelanto", Dinero.formato(r.getMontoAdelanto()));
        m.put("pagado", Dinero.formato(r.getMontoPagado()));
        m.put("saldo", Dinero.formato(r.saldo()));
        m.put("adelantoPct", p.adelantoPct());
        boolean esperaPago = r.getEstado() == EstadoReserva.PENDIENTE
                && (r.getEstadoPago() == EstadoPago.SIN_PAGO || r.getEstadoPago() == EstadoPago.OBSERVADO);
        m.put("aviso", aviso(r, esperaPago));
        m.put("cancelada", c != null);
        if (c != null) {
            m.put("motivoCancelacion", c.getMotivo());
            m.put("fechaCancelacion", c.getFechaCancelacion().format(Tiempo.FECHA_HORA));
            m.put("devolucion", c.isDevolucion() ? "Se devolverá " + Dinero.formato(c.getMontoDevolucion()) + "."
                    : "Sin devolución.");
        }
        m.put("condiciones", condiciones(p, r.getOrigen()));
        m.put("complejo", p.nombreComplejo());
        m.put("direccion", p.direccion());
        m.put("referencia", p.referencia());
        m.put("whatsapp", p.whatsapp());
        m.put("correoComplejo", p.correo());
        m.put("mapaUrl", p.mapaUrl());
        m.put("generado", Tiempo.ahora().format(Tiempo.FECHA_HORA));
        return m;
    }

    private static String aviso(Reserva r, boolean esperaPago) {
        if (esperaPago && r.getVenceEn() != null) {
            return "Paga el adelanto de " + Dinero.formato(r.getMontoAdelanto()) + " antes de las "
                    + Tiempo.horaAmPm(r.getVenceEn().toLocalTime()) + " del " + r.getVenceEn().format(Tiempo.FECHA_CORTA)
                    + "; si no, la reserva se cancelará automáticamente y el turno quedará libre.";
        }
        if (esperaPago) {
            return "Reserva separada en el complejo: paga el adelanto o el total en recepción para confirmarla.";
        }
        if (r.getEstadoPago() == EstadoPago.EN_REVISION && r.getEstado() == EstadoReserva.PENDIENTE) {
            return "Recibimos tu comprobante y lo estamos validando. Te avisaremos por correo y WhatsApp.";
        }
        if (r.getEstado() == EstadoReserva.CONFIRMADA && r.saldo().signum() > 0) {
            return "Paga el saldo de " + Dinero.formato(r.saldo()) + " en el complejo antes de empezar tu partido.";
        }
        return null;
    }

    private static List<String> condiciones(ParametrosComplejo p, Origen origen) {
        List<String> lista = new ArrayList<>();
        lista.add("Llega 10 minutos antes y muestra este código (o el QR) en recepción junto con tu DNI.");
        if (origen == Origen.WEB) {
            lista.add("La reserva se confirma al validar el adelanto del " + p.adelantoPct() + " %. Si no pagas en "
                    + p.minutosParaPagar() + " minutos, se cancela sola.");
        }
        lista.add("Puedes cancelar desde «Mi reserva» hasta antes del inicio del turno.");
        lista.add("Si cancelas con " + p.devolucionHoras() + " horas o más de anticipación, te devolvemos lo pagado; "
                + "con menos tiempo, el adelanto no es reembolsable.");
        lista.add("El turno dura " + p.duracionMin() + " minutos y termina a la hora indicada, aunque empieces tarde.");
        return lista;
    }

    private static String claseEstado(Reserva r) {
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            return "cancelada";
        }
        return r.getEstado() == EstadoReserva.CONFIRMADA ? "confirmada" : "pendiente";
    }

    private static String mayuscula(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** QR (PNG en data URI) en azul noche sobre blanco. */
    static String qr(String contenido) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 360, 360,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida, new MatrixToImageConfig(0xFF091A2A, 0xFFFFFFFF));
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(salida.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }

    /** El logo se reduce una sola vez al arrancar (el original pesa ~250 KB). */
    private static byte[] logoReducido() {
        try (InputStream in = ConstanciaServicioImpl.class.getClassLoader().getResourceAsStream(RAIZ + "logo-alcanfores-horizontal.png");
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IllegalStateException("Falta el logo de la constancia en el classpath");
            }
            BufferedImage original = ImageIO.read(in);
            int alto = Math.round(original.getHeight() * (ANCHO_LOGO / (float) original.getWidth()));
            BufferedImage reducido = new BufferedImage(ANCHO_LOGO, alto, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = reducido.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, ANCHO_LOGO, alto, null);
            g.dispose();
            ImageIO.write(reducido, "PNG", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo de la constancia", e);
        }
    }

    private static void fuente(PdfRendererBuilder pdf, String familia, int peso, String archivo) {
        pdf.useFont(() -> ConstanciaServicioImpl.class.getClassLoader().getResourceAsStream(RAIZ + "fuentes/" + archivo),
                familia, peso, FontStyle.NORMAL, true);
    }
}
