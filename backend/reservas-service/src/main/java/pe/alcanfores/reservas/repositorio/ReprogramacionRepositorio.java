package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.reservas.modelo.ActorReprogramacion;
import pe.alcanfores.reservas.modelo.ReservaReprogramacion;

import java.util.Collection;
import java.util.List;

public interface ReprogramacionRepositorio extends JpaRepository<ReservaReprogramacion, Long> {

    /** Historial de cambios de horario de la reserva, el más reciente primero. */
    List<ReservaReprogramacion> findByIdReservaOrderByCreadoEnDescIdDesc(Long idReserva);

    /** Cambios hechos por esos actores (el cliente y Canchita comparten el límite de uno por reserva). */
    long countByIdReservaAndActorIn(Long idReserva, Collection<ActorReprogramacion> actores);
}
