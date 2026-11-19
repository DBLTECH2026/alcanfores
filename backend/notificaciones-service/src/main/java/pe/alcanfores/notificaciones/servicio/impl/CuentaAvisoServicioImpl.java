package pe.alcanfores.notificaciones.servicio.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.dto.CorreoDtos;
import pe.alcanfores.notificaciones.dto.CorreoDtos.CodigoInternoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.EnvioCodigoRespuesta;
import pe.alcanfores.notificaciones.dto.CorreoDtos.MotivoCodigo;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.MensajeRedactado;
import pe.alcanfores.notificaciones.plantilla.ModeloMensajes;
import pe.alcanfores.notificaciones.plantilla.RedactorMensajes;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.CuentaAvisoServicio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;

import java.util.List;
import java.util.Map;

/**
 * El mensaje se redacta dos veces: con el código real (lo que sale por el canal) y con «******» (lo que se guarda en la
 * bandeja). Así el código nunca queda escrito en la base de datos.
 */
@Service
public class CuentaAvisoServicioImpl implements CuentaAvisoServicio {

    public static final String MASCARA = "******";

    private final RedactorMensajes redactor;
    private final ModeloMensajes modelos;
    private final ComplejoServicio complejo;
    private final EnvioServicio envio;

    public CuentaAvisoServicioImpl(RedactorMensajes redactor, ModeloMensajes modelos, ComplejoServicio complejo,
                                   EnvioServicio envio) {
        this.redactor = redactor;
        this.modelos = modelos;
        this.complejo = complejo;
        this.envio = envio;
    }

    @Override
    public EnvioCodigoRespuesta enviar(CodigoInternoSolicitud s) {
        boolean esCodigo = s.motivo() == MotivoCodigo.RECUPERAR_CONTRASENA;
        if (esCodigo && s.codigo() == null) {
            throw NegocioExcepcion.validacion("codigo", "El código tiene 6 dígitos.");
        }
        String destino = destino(s.canal(), s.destino());
        TipoAviso tipo = esCodigo ? TipoAviso.CODIGO_VERIFICACION : TipoAviso.CONTRASENA_CAMBIADA;
        String asunto = esCodigo ? "Tu código para cambiar la contraseña" : "Tu contraseña se cambió";

        Map<String, Object> m = modelos.base(complejo.datos());
        String nombre = StringUtils.hasText(s.nombre()) ? Texto.titulo(s.nombre()).split(" ")[0] : null;
        m.put("saludo", nombre == null ? "Hola" : "Hola, " + nombre);
        m.put("cambiadoEn", ModeloMensajes.fechaHoraTexto(Tiempo.ahora()));
        m.put("motivoPie", "Recibes este mensaje porque se usó tu cuenta en el sistema de reservas del "
                + m.get("nombreComplejo") + ". Usamos tus datos solo para gestionar tu cuenta y tus reservas");
        List<Adjunto> adjuntos = FabricaAvisos.conTarjeta(tipo, m, List.of());

        m.put("codigoVerificacion", MASCARA);
        String guardado = cuerpo(s.canal(), redactor.redactar(tipo, asunto, m));
        Notificacion n = new Notificacion(null, tipo.evento(), s.canal(), destino, asunto, guardado, adjuntos, Tiempo.ahora());
        if (esCodigo) {
            m.put("codigoVerificacion", s.codigo());
            n.fijarCuerpoEnvio(cuerpo(s.canal(), redactor.redactar(tipo, asunto, m)));
        }
        Notificacion r = envio.enviar(n);
        return new EnvioCodigoRespuesta(r.getEstado().name(), r.getEstado() == EstadoNotificacion.SIMULADO, r.getUltimoError());
    }

    private static String cuerpo(CanalNotificacion canal, MensajeRedactado m) {
        return canal == CanalNotificacion.CORREO ? m.html() : m.texto();
    }

    /** Correo en minúsculas o celular peruano en formato WhatsApp (51XXXXXXXXX); si no calza con el canal, 400. */
    private static String destino(CanalNotificacion canal, String valor) {
        if (canal == CanalNotificacion.CORREO) {
            String correo = Texto.correo(valor);
            if (!correo.matches(CorreoDtos.CORREO)) {
                throw NegocioExcepcion.validacion("destino", "El destino debe ser un correo válido.");
            }
            return correo;
        }
        String numero = FabricaAvisos.numeroWhatsapp(valor);
        if (numero == null) {
            throw NegocioExcepcion.validacion("destino", "El destino debe ser un celular peruano de 9 dígitos.");
        }
        return numero;
    }
}
