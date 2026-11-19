package pe.alcanfores.notificaciones.canal;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.evento.ConsumidorEventos;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** SMTP real contra GreenMail: HTML, logo embebido (CID) y constancia PDF adjunta. */
@TestPropertySource(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "alcanfores.notificaciones.correo.remitente=reservas@alcanfores.pe"
})
class CorreoSmtpTest extends PruebaIntegracion {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private ConsumidorEventos consumidor;

    @BeforeEach
    void pdf() {
        when(reservasCliente.constanciaPdf(anyString())).thenReturn(Datos.PDF);
    }

    private Notificacion correo() {
        return notificaciones.findByCodigoReservaOrderByIdAsc(Datos.CODIGO).stream()
                .filter(n -> n.getCanal() == CanalNotificacion.CORREO).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Con MAIL_HOST el correo sale por SMTP: ENVIADO, HTML, logo inline y PDF adjunto")
    void enviaPorSmtp() throws Exception {
        consumidor.alConfirmarReserva(Datos.confirmada());

        assertThat(correo().getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        MimeMessage[] recibidos = SMTP.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        MimeMessage m = recibidos[0];
        assertThat(m.getSubject()).isEqualTo("Reserva confirmada · ALC-7K3M9P · 20/11/2026 7:00 p. m.");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("diego.ramos@example.com");
        assertThat(m.getFrom()[0].toString()).contains("reservas@alcanfores.pe").contains("Los Alcanfores");

        List<Part> partes = new ArrayList<>();
        recorrer(m, partes);
        assertThat(partes).anySatisfy(p -> {
            assertThat(p.getContentType()).startsWith("text/html");
            assertThat((String) p.getContent()).contains("ALC-7K3M9P", "cid:logo-alcanfores");
        });
        assertThat(partes).anySatisfy(p -> {
            assertThat(p.getContentType()).startsWith("image/png");
            assertThat(((MimeBodyPart) p).getContentID()).isEqualTo("<logo-alcanfores>");
        });
        assertThat(partes).anySatisfy(p -> {
            assertThat(p.getContentType()).startsWith("application/pdf");
            assertThat(p.getFileName()).isEqualTo("constancia-ALC-7K3M9P.pdf");
        });
    }

    @Test
    @DisplayName("Si el SMTP no responde: FALLIDO con el error y 1 intento")
    void smtpCaido() {
        SMTP.stop();

        consumidor.alRegistrarReserva(Datos.registrada());

        Notificacion n = correo();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getUltimoError()).startsWith("El servidor de correo no aceptó el mensaje");
    }

    private static void recorrer(Part parte, List<Part> hojas) throws Exception {
        if (parte.getContent() instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                recorrer(bp, hojas);
            }
        } else {
            hojas.add(parte);
        }
    }
}
