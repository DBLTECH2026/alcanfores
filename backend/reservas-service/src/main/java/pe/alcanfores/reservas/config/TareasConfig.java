package pe.alcanfores.reservas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas salvo que {@code alcanfores.reservas.tareas-habilitadas=false} (pruebas). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "alcanfores.reservas", name = "tareas-habilitadas", havingValue = "true", matchIfMissing = true)
public class TareasConfig {
}
