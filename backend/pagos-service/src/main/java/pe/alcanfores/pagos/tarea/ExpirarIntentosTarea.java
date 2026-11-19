package pe.alcanfores.pagos.tarea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.alcanfores.pagos.servicio.IntentoPagoServicio;

/** Cada minuto: los pagos en línea sin completar pasados sus 15 minutos quedan EXPIRADO. */
@Component
public class ExpirarIntentosTarea {

    private static final Logger log = LoggerFactory.getLogger(ExpirarIntentosTarea.class);
    private final IntentoPagoServicio intentos;

    public ExpirarIntentosTarea(IntentoPagoServicio intentos) {
        this.intentos = intentos;
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void expirar() {
        int n = intentos.expirarVencidos();
        if (n > 0) {
            log.info("{} intento(s) de pago en línea expirado(s)", n);
        }
    }
}
