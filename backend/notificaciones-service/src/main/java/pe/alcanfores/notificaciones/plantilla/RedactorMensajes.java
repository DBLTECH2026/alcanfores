package pe.alcanfores.notificaciones.plantilla;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pe.alcanfores.comun.util.Tiempo;

import java.util.Map;
import java.util.regex.Pattern;

/** Arma el correo (Thymeleaf HTML) y el WhatsApp (Thymeleaf TEXT) de un aviso con el mismo modelo de datos. */
@Component
public class RedactorMensajes {

    private static final Pattern ESPACIOS_FINALES = Pattern.compile("[ \\t]+(?=\\n)");
    private static final Pattern LINEAS_VACIAS = Pattern.compile("\\n{3,}");

    private final SpringTemplateEngine motorCorreo;
    private final SpringTemplateEngine motorWhatsapp;

    public RedactorMensajes(@Qualifier("motorCorreo") SpringTemplateEngine motorCorreo,
                            @Qualifier("motorWhatsapp") SpringTemplateEngine motorWhatsapp) {
        this.motorCorreo = motorCorreo;
        this.motorWhatsapp = motorWhatsapp;
    }

    public MensajeRedactado redactar(TipoAviso tipo, String asunto, Map<String, Object> modelo) {
        Context ctx = new Context(Tiempo.ES_PE, modelo);
        ctx.setVariable("asunto", asunto);
        String html = motorCorreo.process(tipo.plantilla(), ctx);
        String texto = limpiarTexto(motorWhatsapp.process(tipo.plantilla(), ctx));
        return new MensajeRedactado(asunto, html, texto);
    }

    /** Las etiquetas de bloque de Thymeleaf TEXT dejan líneas en blanco de más: se dejan como máximo una. */
    static String limpiarTexto(String texto) {
        String t = texto.replace("\r\n", "\n");
        t = ESPACIOS_FINALES.matcher(t).replaceAll("");
        t = LINEAS_VACIAS.matcher(t).replaceAll("\n\n");
        return t.strip();
    }
}
