package pe.alcanfores.notificaciones.canal;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import org.springframework.mail.MailAuthenticationException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** Traduce las fallas de SMTP a un mensaje amable que dice qué revisar (usuario y clave, servidor, puerto o SSL). */
public final class ErroresCorreo {

    private ErroresCorreo() {
    }

    public static String explicar(Throwable error, String host, int puerto) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof MailAuthenticationException) {
                return "El servidor de correo rechazó el usuario o la contraseña. Revisa que sean los del buzón "
                        + "(en Hostinger, la contraseña del correo, no la del panel de Hostinger).";
            }
            if (t instanceof UnknownHostException) {
                return "No encontramos el servidor «" + host + "». Revisa el nombre (en Hostinger es smtp.hostinger.com).";
            }
            if (t instanceof SSLException) {
                return "Falló la conexión segura con " + host + ":" + puerto + ". Revisa la seguridad: el puerto 465 usa "
                        + "SSL y el 587 usa STARTTLS.";
            }
            if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
                return "No pudimos conectarnos a " + host + " por el puerto " + puerto + ". Revisa el puerto (465 con SSL "
                        + "o 587 con STARTTLS) y que el servidor permita la conexión.";
            }
            if (t instanceof SendFailedException) {
                return "El servidor no aceptó el remitente o el destinatario. En Hostinger el remitente debe ser el mismo "
                        + "correo del usuario.";
            }
            String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("starttls")) {
                return "El servidor " + host + ":" + puerto + " no acepta STARTTLS. Si usas el puerto 465, elige SSL.";
            }
        }
        return "El servidor de correo no aceptó el mensaje: " + error.getMessage();
    }
}
