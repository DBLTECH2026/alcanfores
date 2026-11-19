package pe.alcanfores.notificaciones.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.MensajeRedactado;
import pe.alcanfores.notificaciones.plantilla.RedactorMensajes;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arma los avisos de un hecho para los DOS canales (correo + WhatsApp) con el mismo modelo de datos. Si el cliente no
 * tiene un correo o un celular utilizable, ese canal se omite y se deja constancia en el log.
 */
@Component
public class FabricaAvisos {

    private static final Logger log = LoggerFactory.getLogger(FabricaAvisos.class);
    private final RedactorMensajes redactor;

    public FabricaAvisos(RedactorMensajes redactor) {
        this.redactor = redactor;
    }

    public List<Notificacion> crear(TipoAviso tipo, String asunto, Map<String, Object> modelo, String codigoReserva,
                                    ClienteContacto cliente, List<Adjunto> adjuntos) {
        MensajeRedactado m = redactor.redactar(tipo, asunto, modelo);
        List<Notificacion> avisos = new ArrayList<>(2);
        String correo = cliente == null ? null : Texto.correo(cliente.correo());
        if (StringUtils.hasText(correo)) {
            avisos.add(new Notificacion(codigoReserva, tipo.evento(), CanalNotificacion.CORREO, correo, asunto, m.html(),
                    adjuntos, Tiempo.ahora()));
        } else {
            log.warn("Aviso {} de {} sin correo del cliente: no se envía por correo", tipo.evento(), codigoReserva);
        }
        String whatsapp = numeroWhatsapp(cliente == null ? null : cliente.telefono());
        if (whatsapp != null) {
            avisos.add(new Notificacion(codigoReserva, tipo.evento(), CanalNotificacion.WHATSAPP, whatsapp, asunto, m.texto(),
                    adjuntos, Tiempo.ahora()));
        } else {
            log.warn("Aviso {} de {} sin celular válido: no se envía por WhatsApp", tipo.evento(), codigoReserva);
        }
        return avisos;
    }

    /** «987 654 321» o «+51987654321» → «51987654321»; {@code null} si no es un celular peruano válido. */
    public static String numeroWhatsapp(String telefono) {
        String normalizado = Texto.normalizarCelular(telefono);
        return Documentos.celularValido(normalizado) ? Texto.celularWhatsapp(normalizado) : null;
    }
}
