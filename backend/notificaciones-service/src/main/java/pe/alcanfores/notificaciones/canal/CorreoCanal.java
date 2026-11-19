package pe.alcanfores.notificaciones.canal;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.Tarjetas;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Correo HTML por SMTP con el servidor que indique {@link ProveedorCorreo} (panel, variables {@code MAIL_*} o ninguno).
 * El logo (CID {@value #CID_LOGO}) y la tarjeta ilustrada del aviso (CID {@value Tarjetas#CID}) viajan embebidos para
 * que se vean aunque el cliente de correo bloquee imágenes remotas; los PDFs van como adjuntos.
 */
@Component
public class CorreoCanal implements CanalEnvio {

    public static final String CID_LOGO = "logo-alcanfores";
    public static final String RUTA_LOGO = "correo/logo-alcanfores.png";

    private final ProveedorCorreo proveedor;

    public CorreoCanal(ProveedorCorreo proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public CanalNotificacion canal() {
        return CanalNotificacion.CORREO;
    }

    @Override
    public boolean configurado() {
        return proveedor.vigente().isPresent();
    }

    @Override
    public void enviar(Notificacion n, List<ArchivoAdjunto> archivos) {
        ProveedorCorreo.CorreoVigente c = proveedor.vigente()
                .orElseThrow(() -> new EnvioExcepcion("El correo no está configurado."));
        if (c.problema() != null) {
            throw new EnvioExcepcion(c.problema());
        }
        JavaMailSender emisor = c.emisor();
        try {
            MimeMessage mensaje = emisor.createMimeMessage();
            MimeMessageHelper m = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            m.setFrom(c.remitente(), c.nombreRemitente());
            m.setTo(n.getDestino());
            m.setSubject(n.getAsunto());
            m.setText(n.cuerpoParaEnviar(), true);
            m.addInline(CID_LOGO, new ClassPathResource(RUTA_LOGO), "image/png");
            for (ArchivoAdjunto a : archivos) {
                if (a.imagen()) {
                    m.addInline(Tarjetas.CID, new ByteArrayResource(a.contenido()), ArchivoAdjunto.MIME_JPG);
                } else {
                    m.addAttachment(a.nombre(), new ByteArrayResource(a.contenido()), ArchivoAdjunto.MIME_PDF);
                }
            }
            emisor.send(mensaje);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            throw new EnvioExcepcion(ErroresCorreo.explicar(e, c.host(), c.puerto()), e);
        }
    }
}
