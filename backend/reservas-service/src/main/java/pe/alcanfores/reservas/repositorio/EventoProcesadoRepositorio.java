package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.reservas.modelo.EventoProcesado;

public interface EventoProcesadoRepositorio extends JpaRepository<EventoProcesado, String> {
}
