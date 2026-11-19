package pe.alcanfores.notificaciones.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.notificaciones.modelo.EventoProcesado;

public interface EventoProcesadoRepositorio extends JpaRepository<EventoProcesado, String> {
}
