package pe.alcanfores.notificaciones.canal;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Correo HTML por SMTP ({@code JavaMailSender}, STARTTLS). El logo viaja embebido (CID {@value #CID_LOGO}) para que se
 * vea aunque el cliente de correo bloquee imágenes remotas. Sin {@code MAIL_HOST} el canal no está configurado.
 */
@Component
public class CorreoCanal implements CanalEnvio {

    public static final String CID_LOGO = "logo-alcanfores";
    public static final String RUTA_LOGO = "correo/logo-alcanfores.png";

    private final ObjectProvider<JavaMailSender> correo;
    private final String host;
    private final NotificacionesPropiedades.Correo cfg;

    public CorreoCanal(ObjectProvider<JavaMailSender> correo, @Value("${spring.mail.host:}") String host,
                       NotificacionesPropiedades propiedades) {
        this.correo = correo;
        this.host = host;
        this.cfg = propiedades.getCorreo();
    }

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.CORREO;
    }

    @Override
    public boolean configurado() {
        return StringUtils.hasText(host) && correo.getIfAvailable() != null;
    }

    @Override
    public void enviar(Notificacion n, List<ArchivoPdf> adjuntos) {
        JavaMailSender emisor = correo.getObject();
        try {
            MimeMessage mensaje = emisor.createMimeMessage();
            MimeMessageHelper m = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            m.setFrom(cfg.getRemitente(), cfg.getNombreRemitente());
            m.setTo(n.getDestino());
            m.setSubject(n.getAsunto());
            m.setText(n.getCuerpo(), true);
            m.addInline(CID_LOGO, new ClassPathResource(RUTA_LOGO), "image/png");
            for (ArchivoPdf pdf : adjuntos) {
                m.addAttachment(pdf.nombre(), new ByteArrayResource(pdf.contenido()), "application/pdf");
            }
            emisor.send(mensaje);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            throw new EnvioExcepcion("El servidor de correo no aceptó el mensaje: " + e.getMessage(), e);
        }
    }
}
