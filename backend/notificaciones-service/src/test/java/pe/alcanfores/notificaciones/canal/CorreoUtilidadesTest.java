package pe.alcanfores.notificaciones.canal;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.notificaciones.config.CifradorClaves;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.modelo.SeguridadCorreo;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Cifrado de la contraseña SMTP, propiedades SSL/STARTTLS y mensajes amables de error. */
class CorreoUtilidadesTest {

    private static CifradorClaves cifrador(String claveCifrado, String jwt) {
        NotificacionesPropiedades p = new NotificacionesPropiedades();
        p.getCorreo().setClaveCifrado(claveCifrado);
        AlcanforesPropiedades a = new AlcanforesPropiedades();
        a.getJwt().setSecreto(jwt);
        return new CifradorClaves(p, a);
    }

    @Test
    @DisplayName("AES-GCM: cada cifrado es distinto, se descifra con la misma llave y no con otra")
    void cifrado() {
        CifradorClaves c = cifrador("", "secreto-jwt-de-prueba-con-mas-de-32-bytes");
        String a = c.cifrar("ClaveSmtp2026");
        String b = c.cifrar("ClaveSmtp2026");

        assertThat(a).isNotEqualTo(b).doesNotContain("ClaveSmtp2026");
        assertThat(c.descifrar(a)).contains("ClaveSmtp2026");
        assertThat(cifrador("CORREO_CLAVE_CIFRADO-propia", "secreto-jwt-de-prueba-con-mas-de-32-bytes").descifrar(a)).isEmpty();
        assertThat(c.descifrar("no-es-base64-%%")).isEmpty();
    }

    @Test
    @DisplayName("SSL directo para el 465 y STARTTLS obligatorio para el 587, con tiempos acotados")
    void propiedades() {
        Properties ssl = ProveedorCorreo.propiedades(SeguridadCorreo.SSL, "");
        assertThat(ssl).containsEntry("mail.smtp.ssl.enable", "true").containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.timeout", "10000").doesNotContainKey("mail.smtp.starttls.enable")
                .doesNotContainKey("mail.smtp.ssl.trust");
        Properties tls = ProveedorCorreo.propiedades(SeguridadCorreo.STARTTLS, "smtp.propio.pe");
        assertThat(tls).containsEntry("mail.smtp.starttls.enable", "true").containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.trust", "smtp.propio.pe").containsEntry("mail.smtp.ssl.checkserveridentity", "false")
                .doesNotContainKey("mail.smtp.ssl.enable");
    }

    @Test
    @DisplayName("Cada falla de SMTP se explica: usuario y clave, servidor, SSL, puerto, remitente y STARTTLS")
    void errores() {
        String h = "smtp.hostinger.com";
        assertThat(ErroresCorreo.explicar(new MailSendException("x", new AuthenticationFailedException("535")), h, 465))
                .contains("rechazó el usuario o la contraseña");
        assertThat(ErroresCorreo.explicar(new MessagingException("x", new UnknownHostException(h)), h, 465))
                .contains("No encontramos el servidor «smtp.hostinger.com»");
        assertThat(ErroresCorreo.explicar(new MessagingException("x", new SSLHandshakeException("hs")), h, 587))
                .contains("conexión segura", "465 usa SSL");
        assertThat(ErroresCorreo.explicar(new MessagingException("x", new ConnectException("refused")), h, 25))
                .contains("por el puerto 25");
        assertThat(ErroresCorreo.explicar(new SendFailedException("550"), h, 465)).contains("remitente o el destinatario");
        assertThat(ErroresCorreo.explicar(new MessagingException("STARTTLS is required but host does not support STARTTLS"),
                h, 465)).contains("no acepta STARTTLS");
        assertThat(ErroresCorreo.explicar(new MessagingException("421 ocupado"), h, 465))
                .isEqualTo("El servidor de correo no aceptó el mensaje: 421 ocupado");
    }
}
