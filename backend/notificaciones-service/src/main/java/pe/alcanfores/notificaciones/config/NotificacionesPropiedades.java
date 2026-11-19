package pe.alcanfores.notificaciones.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Propiedades propias del servicio ({@code alcanfores.notificaciones.*}). Todas tienen valor por defecto para local;
 * en producción llegan por variables de entorno (ver application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "alcanfores.notificaciones")
public class NotificacionesPropiedades {

    private final Correo correo = new Correo();
    private final Evolution evolution = new Evolution();
    private final Recordatorio recordatorio = new Recordatorio();
    private final Complejo complejo = new Complejo();
    /** Máximo de intentos automáticos de un aviso FALLIDO (el admin puede reintentar a mano después). */
    private int maxIntentos = 3;
    /** Carga avisos de ejemplo al arrancar si la bandeja está vacía (solo para la demo local). */
    private boolean datosDemo = false;

    @Getter
    @Setter
    public static class Correo {
        /** Dirección del remitente ({@code MAIL_REMITENTE}). */
        private String remitente = "informes.alcanfores@gmail.com";
        private String nombreRemitente = "Complejo Deportivo Los Alcanfores";
        /** Clave para cifrar la contraseña SMTP guardada desde el panel ({@code CORREO_CLAVE_CIFRADO}; vacía = JWT_SECRETO). */
        private String claveCifrado = "";
    }

    @Getter
    @Setter
    public static class Evolution {
        /** URL base de Evolution API v2, p. ej. {@code https://evolution.midominio.pe} ({@code EVOLUTION_URL}). */
        private String url = "";
        /** Clave global o de la instancia; viaja en la cabecera {@code apikey} ({@code EVOLUTION_APIKEY}). */
        private String apikey = "";
        private String instancia = "alcanfores";
        private int timeoutSegundos = 20;

        public boolean configurado() {
            return StringUtils.hasText(url) && StringUtils.hasText(apikey);
        }
    }

    @Getter
    @Setter
    public static class Recordatorio {
        /** Minutos antes del inicio si catalogo-service no responde ({@code recordatorio_minutos}). */
        private List<Integer> minutosPorDefecto = List.of(120);
        /** Si la hora de envío pasó hace más que esto (p. ej. la reserva se confirmó tarde), el recordatorio se OMITE. */
        private int toleranciaMinutos = 10;
    }

    /** Datos del complejo que se usan si catalogo-service no responde (los reales del cliente). */
    @Getter
    @Setter
    public static class Complejo {
        private String nombre = "Complejo Deportivo Los Alcanfores";
        private String direccion = "Carretera Central km 11.5, Santa Clara, Ate";
        private String referencia = "Paradero Caseta, a dos cuadras de la UTP Santa Clara";
        private String mapaUrl = "https://www.google.com/maps/search/?api=1&query=Complejo+Deportivo+Los+Alcanfores+Santa+Clara+Ate";
        private String whatsapp = "914995187";
        private String correo = "informes.alcanfores@gmail.com";
    }
}
