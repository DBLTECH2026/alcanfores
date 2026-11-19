package pe.alcanfores.notificaciones.canal;

import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.util.List;

/** Un medio de entrega (correo, WhatsApp). Si no está configurado, el aviso se guarda como SIMULADO. */
public interface CanalEnvio {

    CanalNotificacion canal();

    boolean configurado();

    /** @throws EnvioExcepcion si el proveedor rechaza o no responde. */
    void enviar(Notificacion notificacion, List<ArchivoPdf> adjuntos);
}
