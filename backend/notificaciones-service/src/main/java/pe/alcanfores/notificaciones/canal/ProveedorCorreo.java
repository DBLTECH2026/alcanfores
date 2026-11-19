package pe.alcanfores.notificaciones.canal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pe.alcanfores.notificaciones.config.CifradorClaves;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.modelo.ConfigCorreo;
import pe.alcanfores.notificaciones.modelo.SeguridadCorreo;
import pe.alcanfores.notificaciones.repositorio.ConfigCorreoRepositorio;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Decide con qué servidor sale el correo: la configuración del panel (tabla {@code config_correo}) si existe; si no,
 * las variables {@code MAIL_*}; si tampoco, ninguno (los avisos quedan SIMULADOS). El {@code JavaMailSender} se arma
 * una vez y queda en caché hasta que el panel guarde otra configuración ({@link #invalidar()}).
 */
@Component
public class ProveedorCorreo {

    /** Tiempo máximo para conectar, leer y escribir con el servidor SMTP (ms). */
    static final String TIEMPO_MS = "10000";
    private static final Logger log = LoggerFactory.getLogger(ProveedorCorreo.class);

    public enum Origen { PANEL, VARIABLES, NINGUNO }

    /**
     * Servidor vigente. {@code problema}: motivo por el que no se puede usar aunque esté guardado (p. ej. la contraseña
     * no se pudo descifrar porque cambió la llave del servidor).
     */
    public record CorreoVigente(Origen origen, JavaMailSender emisor, String host, int puerto, String remitente,
                                String nombreRemitente, String problema) {
    }

    private final ConfigCorreoRepositorio configuraciones;
    private final CifradorClaves cifrador;
    private final ObjectProvider<JavaMailSender> emisorVariables;
    private final NotificacionesPropiedades.Correo porDefecto;
    private final String hostVariables;
    private final int puertoVariables;
    private final String confiarSsl;
    private volatile Optional<CorreoVigente> enCache;

    public ProveedorCorreo(ConfigCorreoRepositorio configuraciones, CifradorClaves cifrador,
                           ObjectProvider<JavaMailSender> emisorVariables, NotificacionesPropiedades propiedades,
                           @Value("${spring.mail.host:}") String hostVariables,
                           @Value("${spring.mail.port:587}") int puertoVariables,
                           @Value("${alcanfores.notificaciones.correo.ssl-confiar:}") String confiarSsl) {
        this.configuraciones = configuraciones;
        this.cifrador = cifrador;
        this.emisorVariables = emisorVariables;
        this.porDefecto = propiedades.getCorreo();
        this.hostVariables = hostVariables;
        this.puertoVariables = puertoVariables;
        this.confiarSsl = confiarSsl;
    }

    public Optional<CorreoVigente> vigente() {
        Optional<CorreoVigente> c = enCache;
        if (c == null) {
            c = cargar();
            enCache = c;
        }
        return c;
    }

    /** Tras guardar desde el panel: el siguiente envío vuelve a leer la configuración. */
    public void invalidar() {
        enCache = null;
    }

    private Optional<CorreoVigente> cargar() {
        Optional<ConfigCorreo> panel = configuraciones.findById(ConfigCorreo.ID_UNICO);
        if (panel.isPresent()) {
            ConfigCorreo c = panel.get();
            Optional<String> clave = c.tieneClave() ? cifrador.descifrar(c.getClaveCifrada()) : Optional.empty();
            String problema = c.tieneClave() && clave.isEmpty()
                    ? "La contraseña del correo guardada no se pudo leer (cambió la llave del servidor). "
                    + "Vuelve a escribirla en el panel."
                    : null;
            if (problema != null) {
                log.warn("Configuración de correo del panel: {}", problema);
            }
            return Optional.of(new CorreoVigente(Origen.PANEL, emisor(c, clave.orElse(null)), c.getHost(), c.getPuerto(),
                    c.getRemitente(), c.getNombreRemitente(), problema));
        }
        JavaMailSender variables = emisorVariables.getIfAvailable();
        if (StringUtils.hasText(hostVariables) && variables != null) {
            return Optional.of(new CorreoVigente(Origen.VARIABLES, variables, hostVariables, puertoVariables,
                    porDefecto.getRemitente(), porDefecto.getNombreRemitente(), null));
        }
        return Optional.empty();
    }

    private JavaMailSenderImpl emisor(ConfigCorreo c, String clave) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(c.getHost());
        s.setPort(c.getPuerto());
        s.setUsername(c.getUsuario());
        s.setPassword(clave);
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        s.setJavaMailProperties(propiedades(c.getSeguridad(), confiarSsl));
        return s;
    }

    /**
     * SSL directo (465) o STARTTLS obligatorio (587), siempre con autenticación y tiempos acotados. {@code confiarSsl}
     * ({@code alcanfores.notificaciones.correo.ssl-confiar}, vacío por defecto) solo sirve para un servidor propio con
     * certificado autofirmado (y las pruebas).
     */
    static Properties propiedades(SeguridadCorreo seguridad, String confiarSsl) {
        Properties p = new Properties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.connectiontimeout", TIEMPO_MS);
        p.put("mail.smtp.timeout", TIEMPO_MS);
        p.put("mail.smtp.writetimeout", TIEMPO_MS);
        if (seguridad == SeguridadCorreo.SSL) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        if (StringUtils.hasText(confiarSsl)) {
            p.put("mail.smtp.ssl.trust", confiarSsl);
            p.put("mail.smtp.ssl.checkserveridentity", "false");
        }
        return p;
    }
}
