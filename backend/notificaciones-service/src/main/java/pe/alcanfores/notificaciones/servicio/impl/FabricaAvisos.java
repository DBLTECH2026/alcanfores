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
import pe.alcanfores.notificaciones.plantilla.Tarjetas;
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

    /**
     * Con la tarjeta ilustrada del aviso (según el deporte en el recordatorio) como primer adjunto: imagen del WhatsApp y
     * cabecera del correo ({@code tarjetaSrc}).
     */
    public List<Notificacion> crear(TipoAviso tipo, String asunto, Map<String, Object> modelo, String codigoReserva,
                                    ClienteContacto cliente, List<Adjunto> adjuntos) {
        List<Adjunto> conTarjeta = conTarjeta(tipo, modelo, adjuntos);
        MensajeRedactado m = redactor.redactar(tipo, asunto, modelo);
        List<Notificacion> avisos = new ArrayList<>(2);
        String correo = cliente == null ? null : Texto.correo(cliente.correo());
        if (StringUtils.hasText(correo)) {
            avisos.add(new Notificacion(codigoReserva, tipo.evento(), CanalNotificacion.CORREO, correo, asunto, m.html(),
                    conTarjeta, Tiempo.ahora()));
        } else {
            log.warn("Aviso {} de {} sin correo del cliente: no se envía por correo", tipo.evento(), codigoReserva);
        }
        String whatsapp = numeroWhatsapp(cliente == null ? null : cliente.telefono());
        if (whatsapp != null) {
            avisos.add(new Notificacion(codigoReserva, tipo.evento(), CanalNotificacion.WHATSAPP, whatsapp, asunto, m.texto(),
                    conTarjeta, Tiempo.ahora()));
        } else {
            log.warn("Aviso {} de {} sin celular válido: no se envía por WhatsApp", tipo.evento(), codigoReserva);
        }
        return avisos;
    }

    /** Antepone la tarjeta del aviso a los adjuntos y deja su CID en el modelo para la cabecera del correo. */
    public static List<Adjunto> conTarjeta(TipoAviso tipo, Map<String, Object> modelo, List<Adjunto> adjuntos) {
        String tarjeta = tipo.tarjeta((String) modelo.get("deporteCodigo"));
        if (tarjeta == null) {
            return adjuntos;
        }
        modelo.put("tarjetaSrc", "cid:" + Tarjetas.CID);
        List<Adjunto> todos = new ArrayList<>(adjuntos.size() + 1);
        todos.add(Adjunto.tarjeta(tarjeta));
        todos.addAll(adjuntos);
        return todos;
    }

    /** «987 654 321» o «+51987654321» → «51987654321»; {@code null} si no es un celular peruano válido. */
    public static String numeroWhatsapp(String telefono) {
        String normalizado = Texto.normalizarCelular(telefono);
        return Documentos.celularValido(normalizado) ? Texto.celularWhatsapp(normalizado) : null;
    }
}
