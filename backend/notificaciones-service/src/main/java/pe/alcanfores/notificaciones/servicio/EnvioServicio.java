package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.util.List;

/** Entrega avisos ya guardados: descarga sus PDFs, los manda por su canal y deja ENVIADO, SIMULADO o FALLIDO. */
public interface EnvioServicio {

    /** Envía varios avisos de un mismo hecho; cada PDF se descarga una sola vez para todos. */
    List<Notificacion> enviarTodas(List<Notificacion> notificaciones);

    Notificacion enviar(Notificacion notificacion);
}
