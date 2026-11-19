package pe.alcanfores.reservas.tarea;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;

/**
 * Tareas programadas (zona America/Lima): al arrancar lee catálogo y genera los turnos; cada minuto expira las
 * reservas sin pago; a las 00:05 genera los turnos del nuevo día; cada 5 minutos refresca catálogo y, mientras catálogo
 * no se haya podido leer (arrancó después), lo reintenta cada 30 segundos.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "alcanfores.reservas", name = "tareas-habilitadas", havingValue = "true", matchIfMissing = true)
public class TareasProgramadas implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final TurnoServicio turnos;
    private final ExpiracionServicio expiracion;

    @Override
    public void run(String... args) {
        turnos.sincronizarCatalogo();
        generarTurnos();
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void expirarReservas() {
        expiracion.expirarVencidas();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    public void generarTurnos() {
        var r = turnos.generarVentana();
        log.info("Ventana de turnos al día: {} creados, {} ya existían", r.creados(), r.existentes());
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void refrescarCatalogo() {
        turnos.sincronizarCatalogo();
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void reintentarCatalogo() {
        turnos.sincronizarSiFalta();
    }
}
