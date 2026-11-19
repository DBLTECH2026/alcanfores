package pe.alcanfores.notificaciones.servicio.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.canal.ProveedorCorreo;
import pe.alcanfores.notificaciones.config.CifradorClaves;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.dto.CorreoDtos.ConfigCorreoRespuesta;
import pe.alcanfores.notificaciones.dto.CorreoDtos.GuardarCorreoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.PruebaCorreoRespuesta;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.ConfigCorreo;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.ModeloMensajes;
import pe.alcanfores.notificaciones.plantilla.RedactorMensajes;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.repositorio.ConfigCorreoRepositorio;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.CorreoServicio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;

import java.util.List;
import java.util.Map;

@Service
public class CorreoServicioImpl implements CorreoServicio {

    static final String NO_ENVIADO = "CORREO_NO_ENVIADO";

    private final ConfigCorreoRepositorio configuraciones;
    private final CifradorClaves cifrador;
    private final ProveedorCorreo proveedor;
    private final NotificacionRepositorio notificaciones;
    private final EnvioServicio envio;
    private final RedactorMensajes redactor;
    private final ModeloMensajes modelos;
    private final ComplejoServicio complejo;
    private final Auditor auditor;
    private final NotificacionesPropiedades.Correo porDefecto;
    private final String hostVariables;
    private final int puertoVariables;
    private final String usuarioVariables;
    private final String claveVariables;
    private final boolean starttlsVariables;

    public CorreoServicioImpl(ConfigCorreoRepositorio configuraciones, CifradorClaves cifrador, ProveedorCorreo proveedor,
                              NotificacionRepositorio notificaciones, EnvioServicio envio, RedactorMensajes redactor,
                              ModeloMensajes modelos, ComplejoServicio complejo, Auditor auditor,
                              NotificacionesPropiedades propiedades,
                              @Value("${spring.mail.host:}") String hostVariables,
                              @Value("${spring.mail.port:587}") int puertoVariables,
                              @Value("${spring.mail.username:}") String usuarioVariables,
                              @Value("${spring.mail.password:}") String claveVariables,
                              @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean starttlsVariables) {
        this.configuraciones = configuraciones;
        this.cifrador = cifrador;
        this.proveedor = proveedor;
        this.notificaciones = notificaciones;
        this.envio = envio;
        this.redactor = redactor;
        this.modelos = modelos;
        this.complejo = complejo;
        this.auditor = auditor;
        this.porDefecto = propiedades.getCorreo();
        this.hostVariables = hostVariables;
        this.puertoVariables = puertoVariables;
        this.usuarioVariables = usuarioVariables;
        this.claveVariables = claveVariables;
        this.starttlsVariables = starttlsVariables;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigCorreoRespuesta obtener() {
        return configuraciones.findById(ConfigCorreo.ID_UNICO).map(CorreoServicioImpl::desdePanel).orElseGet(() -> {
            if (StringUtils.hasText(hostVariables)) {
                return new ConfigCorreoRespuesta(true, ProveedorCorreo.Origen.VARIABLES.name(), hostVariables,
                        puertoVariables, usuarioVariables, porDefecto.getRemitente(), porDefecto.getNombreRemitente(),
                        starttlsVariables ? "STARTTLS" : "SSL", StringUtils.hasText(claveVariables), null, null);
            }
            return new ConfigCorreoRespuesta(false, ProveedorCorreo.Origen.NINGUNO.name(), null, null, null, null,
                    porDefecto.getNombreRemitente(), null, false, null, null);
        });
    }

    @Override
    @Transactional
    public ConfigCorreoRespuesta guardar(GuardarCorreoSolicitud s) {
        ConfigCorreo c = configuraciones.findById(ConfigCorreo.ID_UNICO).orElseGet(ConfigCorreo::new);
        boolean claveNueva = StringUtils.hasText(s.clave());
        if (!claveNueva && !c.tieneClave()) {
            throw NegocioExcepcion.validacion("clave", "Ingresa la contraseña del correo.");
        }
        c.setHost(s.host().trim().toLowerCase());
        c.setPuerto(s.puerto());
        c.setUsuario(Texto.correo(s.usuario()));
        if (claveNueva) {
            c.setClaveCifrada(cifrador.cifrar(s.clave()));
        }
        c.setRemitente(Texto.correo(s.remitente()));
        c.setNombreRemitente(StringUtils.hasText(s.nombreRemitente()) ? Texto.limpiar(s.nombreRemitente())
                : porDefecto.getNombreRemitente());
        c.setSeguridad(s.seguridad());
        c.setActualizadoEn(Tiempo.ahora());
        c.setActualizadoPor(SeguridadUtil.actual().map(UsuarioToken::usuario).orElse(null));
        configuraciones.save(c);
        proveedor.invalidar();
        auditor.registrar("CONFIGURAR_CORREO", "config_correo", ConfigCorreo.ID_UNICO, "Servidor " + c.getHost() + ":"
                + c.getPuerto() + " " + c.getSeguridad() + " · usuario " + c.getUsuario() + " · remitente " + c.getRemitente()
                + (claveNueva ? " · contraseña actualizada" : ""));
        return desdePanel(c);
    }

    @Override
    public PruebaCorreoRespuesta probar(String destino) {
        ProveedorCorreo.CorreoVigente c = proveedor.vigente().orElseThrow(() -> NegocioExcepcion.regla(NO_ENVIADO,
                "Aún no hay un correo configurado: guarda los datos del servidor y vuelve a probar."));
        String correo = Texto.correo(destino);
        Map<String, Object> m = modelos.base(complejo.datos());
        m.put("servidor", c.host() + ":" + c.puerto());
        m.put("remitenteTexto", c.nombreRemitente() + " <" + c.remitente() + ">");
        m.put("motivoPie", "Recibes este mensaje porque el administrador probó el correo del sistema de reservas");
        String asunto = "Prueba de correo · " + c.nombreRemitente();
        String html = redactor.redactarCorreo(TipoAviso.PRUEBA_CORREO, asunto, m);
        Notificacion enviada = envio.enviar(new Notificacion(null, TipoAviso.PRUEBA_CORREO.evento(), CanalNotificacion.CORREO,
                correo, asunto, html, List.of(), Tiempo.ahora()));
        auditor.registrar("PRUEBA_CORREO", "notificacion", enviada.getId(),
                "Correo de prueba a " + correo + " por " + c.host() + ":" + c.puerto() + " · resultado " + enviada.getEstado());
        if (enviada.getEstado() != EstadoNotificacion.ENVIADO) {
            throw NegocioExcepcion.regla(NO_ENVIADO, "No se pudo enviar el correo de prueba. " + enviada.getUltimoError());
        }
        return new PruebaCorreoRespuesta(true, "Enviamos un correo de prueba a " + correo
                + ". Revisa la bandeja de entrada (y la carpeta de spam).");
    }

    private static ConfigCorreoRespuesta desdePanel(ConfigCorreo c) {
        return new ConfigCorreoRespuesta(true, ProveedorCorreo.Origen.PANEL.name(), c.getHost(), c.getPuerto(), c.getUsuario(),
                c.getRemitente(), c.getNombreRemitente(), c.getSeguridad().name(), c.tieneClave(), c.getActualizadoEn(),
                c.getActualizadoPor());
    }
}
