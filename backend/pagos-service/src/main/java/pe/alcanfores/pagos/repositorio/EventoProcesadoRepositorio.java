package pe.alcanfores.pagos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.pagos.modelo.EventoProcesado;

public interface EventoProcesadoRepositorio extends JpaRepository<EventoProcesado, String> {
}
