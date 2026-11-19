package pe.alcanfores.pagos.tarea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import pe.alcanfores.pagos.servicio.SemillaPagos;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Al arrancar, si no hay pagos y {@code alcanfores.semilla.habilitada=true}, pide a reservas-service sus reservas de
 * ejemplo cada 30 s (máximo 20 intentos: reservas puede tardar más en levantar) y crea los pagos de la semilla.
 */
@Component
public class SemillaTarea {

    private static final Logger log = LoggerFactory.getLogger(SemillaTarea.class);
    static final int MAX_INTENTOS = 20;
    static final Duration ESPERA = Duration.ofSeconds(30);

    private final SemillaPagos semilla;
    private final TaskScheduler programador;
    private final boolean habilitada;
    private final AtomicInteger intentos = new AtomicInteger();
    private volatile ScheduledFuture<?> tarea;
    private volatile boolean terminada;

    public SemillaTarea(SemillaPagos semilla, TaskScheduler programador,
                        @Value("${alcanfores.semilla.habilitada:true}") boolean habilitada) {
        this.semilla = semilla;
        this.programador = programador;
        this.habilitada = habilitada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        if (!habilitada) {
            return;
        }
        if (semilla.hayPagos()) {
            log.info("Semilla de pagos omitida: ya hay pagos registrados");
            return;
        }
        tarea = programador.scheduleWithFixedDelay(this::intentar, ESPERA);
    }

    void intentar() {
        if (terminada) {
            detener();
            return;
        }
        int n = intentos.incrementAndGet();
        try {
            int creados = semilla.ejecutar();
            log.info("Semilla de pagos lista en el intento {}: {} pago(s)", n, creados);
            detener();
        } catch (RuntimeException e) {
            if (n >= MAX_INTENTOS) {
                log.warn("Semilla de pagos abandonada tras {} intentos: {}", n, e.getMessage());
                detener();
            } else {
                log.info("Semilla de pagos: reservas-service aún no responde (intento {}/{}): {}", n, MAX_INTENTOS, e.getMessage());
            }
        }
    }

    private void detener() {
        terminada = true;
        if (tarea != null) {
            tarea.cancel(false);
        }
    }
}
