package pe.alcanfores.notificaciones.plantilla;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.config.PlantillasConfigAcceso;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada plantilla renderiza en HTML (correo) y TEXT (WhatsApp) con la marca, sin variables sin resolver ni emojis. */
class PlantillasTest {

    /** Rangos de emojis y pictogramas: los mensajes no deben llevar ninguno. */
    private static final Pattern EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}]");

    private RedactorMensajes redactor;
    private ModeloMensajes modelos;

    @BeforeEach
    void preparar() {
        Datos.reloj(LocalDateTime.of(2026, 11, 20, 17, 0));
        SpringTemplateEngine correo = PlantillasConfigAcceso.motorCorreo();
        SpringTemplateEngine whatsapp = PlantillasConfigAcceso.motorWhatsapp();
        redactor = new RedactorMensajes(correo, whatsapp);
        AlcanforesPropiedades p = new AlcanforesPropiedades();
        p.setFrontUrl("https://alcanfores.pe/,https://www.alcanfores.pe");
        modelos = new ModeloMensajes(p);
    }

    @AfterEach
    void restablecer() {
        Tiempo.restablecer();
    }

    /** Modelo con TODAS las variables que usan las plantillas (cada aviso usa un subconjunto). */
    private Map<String, Object> modeloCompleto() {
        Map<String, Object> m = modelos.reserva(Datos.reserva(), Datos.complejo());
        modelos.fijarSaldo(m, new java.math.BigDecimal("40.00"));
        m.put("conPlazo", true);
        m.put("plazo", "7:20 p. m. de hoy");
        m.put("metodo", "Yape");
        m.put("montoPago", "S/ 40.00");
        m.put("tipoPago", "adelanto");
        m.put("observacion", "La imagen está borrosa");
        m.put("tipoComprobante", "boleta electrónica");
        m.put("notaCredito", false);
        m.put("serieNumero", "B001-00000012");
        m.put("total", "S/ 40.00");
        m.put("fechaEmision", "jueves 19 de noviembre, 10:06 a. m.");
        m.put("conReserva", true);
        m.put("motivo", "Lluvia intensa");
        m.put("canceladoPor", "el complejo");
        m.put("porSistema", false);
        m.put("devolucion", true);
        m.put("montoDevolucion", "S/ 40.00");
        m.put("sinDevolucion", false);
        m.put("falta", "2 horas");
        m.put("cuando", "de hoy");
        return m;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoAviso.class, names = "PRUEBA_WHATSAPP", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Cada aviso renderiza correo HTML con la marca y WhatsApp en texto")
    void renderiza(TipoAviso tipo) {
        MensajeRedactado m = redactor.redactar(tipo, "Asunto de prueba", modeloCompleto());

        assertThat(m.html())
                .startsWith("<!DOCTYPE html>")
                .contains("<title>Asunto de prueba</title>", "cid:logo-alcanfores", "#27811F", "#091A2A", "#E8F3EC",
                        "Carretera Central km 11.5", "+51 914 995 187", "https://wa.me/51914995187",
                        "Ley N.° 29733", "https://alcanfores.pe/privacidad", "max-width:600px", "@media only screen")
                .doesNotContain("${", "th:text", "th:replace", "th:if", "xmlns:th", "~{");
        assertThat(m.texto())
                .startsWith("*")
                .endsWith("Complejo Deportivo Los Alcanfores")
                .doesNotContain("${", "[(", "[#", "[/]", "\n\n\n")
                .hasSizeLessThan(1000);
        assertThat(EMOJI.matcher(m.html()).find()).as("sin emojis en el correo").isFalse();
        assertThat(EMOJI.matcher(m.texto()).find()).as("sin emojis en el WhatsApp").isFalse();
    }

    @Test
    @DisplayName("Enlaces del portal con la primera FRONT_URL y botón principal")
    void enlaces() {
        MensajeRedactado m = redactor.redactar(TipoAviso.RESERVA_CONFIRMADA, "x", modeloCompleto());

        assertThat(m.html()).contains("href=\"https://alcanfores.pe/mi-reserva?codigo=ALC-7K3M9P\"", "Ver mi reserva",
                "background-color:#27811F", "Abrir en Google Maps");
        assertThat(redactor.redactar(TipoAviso.INVITACION_RESENA, "x", modeloCompleto()).texto())
                .contains("https://alcanfores.pe/canchas/1?resena=ALC-7K3M9P");
        assertThat(redactor.redactar(TipoAviso.RESERVA_REGISTRADA, "x", modeloCompleto()).html())
                .contains("https://alcanfores.pe/reservar/listo/ALC-7K3M9P");
    }

    @Test
    @DisplayName("Tarjeta de la reserva: código grande, cancha, fecha, hora y montos")
    void tarjeta() {
        String html = redactor.redactar(TipoAviso.RESERVA_CONFIRMADA, "x", modeloCompleto()).html();

        assertThat(html).contains("Código de reserva", "ALC-7K3M9P", "Cancha 1 · Fútbol 5", "Viernes 20 de noviembre de 2026",
                "7:00–8:00 p. m.", "S/ 80.00", "Saldo a pagar en el complejo");
    }

    @Test
    @DisplayName("Los textos condicionales cambian según el caso (sin plazo, sin saldo, sistema, nota de crédito)")
    void condicionales() {
        Map<String, Object> m = modeloCompleto();
        m.put("conPlazo", false);
        m.put("haySaldo", false);
        m.put("porSistema", true);
        m.put("notaCredito", true);
        m.put("devolucion", false);
        m.put("sinDevolucion", true);

        assertThat(redactor.redactar(TipoAviso.RESERVA_REGISTRADA, "x", m).texto())
                .contains("Puedes pagar el adelanto en el complejo").doesNotContain("Plazo para pagar");
        assertThat(redactor.redactar(TipoAviso.RESERVA_CONFIRMADA, "x", m).texto()).contains("Pagaste el total");
        assertThat(redactor.redactar(TipoAviso.RESERVA_CANCELADA, "x", m).texto())
                .contains("No recibimos el pago del adelanto a tiempo", "no tienen devolución");
        assertThat(redactor.redactar(TipoAviso.COMPROBANTE_EMITIDO, "x", m).html()).contains("Nota de crédito B001-00000012");
    }

    @Test
    @DisplayName("Limpieza del texto: sin espacios al final ni más de una línea en blanco seguida")
    void limpieza() {
        assertThat(RedactorMensajes.limpiarTexto("\n *Hola*  \r\n\n\n\nlinea  \n\n")).isEqualTo("*Hola*\n\nlinea");
    }
}
