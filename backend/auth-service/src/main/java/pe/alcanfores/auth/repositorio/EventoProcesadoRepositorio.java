package pe.alcanfores.auth.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.auth.modelo.EventoProcesado;

public interface EventoProcesadoRepositorio extends JpaRepository<EventoProcesado, String> {
}
