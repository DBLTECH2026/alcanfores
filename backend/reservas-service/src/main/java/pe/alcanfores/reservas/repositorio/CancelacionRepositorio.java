package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.Cancelacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CancelacionRepositorio extends JpaRepository<Cancelacion, Long> {

    Optional<Cancelacion> findByReservaId(Long idReserva);

    @Query("select c from Cancelacion c where c.reserva.id in :ids")
    List<Cancelacion> deReservas(@Param("ids") Collection<Long> idsReserva);
}
