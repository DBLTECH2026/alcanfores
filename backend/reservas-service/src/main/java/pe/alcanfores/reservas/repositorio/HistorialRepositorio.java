package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.reservas.modelo.ReservaHistorial;

import java.util.List;

public interface HistorialRepositorio extends JpaRepository<ReservaHistorial, Long> {

    List<ReservaHistorial> findByIdReservaOrderByFechaAscIdAsc(Long idReserva);
}
