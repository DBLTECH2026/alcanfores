package pe.alcanfores.notificaciones.tarea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.alcanfores.notificaciones.servicio.NotificacionServicio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

/**
 * Tareas programadas (zona America/Lima). Se pueden apagar con {@code alcanfores.notificaciones.tareas-habilitadas=false}
 * (las pruebas llaman a los servicios directamente).
 */
@Component
@ConditionalOnProperty(prefix = "alcanfores.notificaciones", name = "tareas-habilitadas", havingValue = "true", matchIfMissing = true)
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final RecordatorioServicio recordatorios;
    private final NotificacionServicio notificaciones;

    public TareasProgramadas(RecordatorioServicio recordatorios, NotificacionServicio notificaciones) {
        this.recordatorios = recordatorios;
        this.notificaciones = notificaciones;
    }

    /** Cada minuto: envía los recordatorios cuya hora llegó. */
    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void enviarRecordatorios() {
        try {
            recordatorios.ejecutarPendientes();
        } catch (RuntimeException e) {
            log.error("Falló la tarea de recordatorios", e);
        }
    }

    /** Cada 5 minutos: reintenta los avisos FALLIDOS con menos de 3 intentos. */
    @Scheduled(cron = "30 */5 * * * *", zone = "America/Lima")
    public void reintentarFallidas() {
        try {
            notificaciones.reintentarFallidas();
        } catch (RuntimeException e) {
            log.error("Falló la tarea de reintentos", e);
        }
    }
}
