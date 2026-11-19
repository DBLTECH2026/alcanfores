package pe.alcanfores.reservas.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.Turno;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TurnoRepositorio extends JpaRepository<Turno, Long> {

    /** {@code SELECT … FOR UPDATE}: bloquea el turno hasta el fin de la transacción (dos clientes no lo toman a la vez). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.id = :id")
    Optional<Turno> bloquearPorId(@Param("id") Long id);

    /**
     * {@code SELECT … FOR UPDATE} de varios turnos, SIEMPRE en orden de id: dos reservas que comparten turnos los piden en
     * el mismo orden y no se interbloquean (la segunda espera y luego ve el turno ocupado).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.id in :ids order by t.id")
    List<Turno> bloquearPorIds(@Param("ids") Collection<Long> ids);

    List<Turno> findByFechaOrderByIdCanchaAscHoraInicioAsc(LocalDate fecha);

    List<Turno> findByFechaBetweenOrderByFechaAscIdCanchaAscHoraInicioAsc(LocalDate desde, LocalDate hasta);

    boolean existsByFecha(LocalDate fecha);

    /** Turnos de una cancha en un rango de fechas, bloqueados para regenerarlos sin pisar una reserva concurrente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.idCancha = :idCancha and t.fecha between :desde and :hasta order by t.id")
    List<Turno> bloquearDeCanchaEntre(@Param("idCancha") Long idCancha, @Param("desde") LocalDate desde,
                                      @Param("hasta") LocalDate hasta);

    /** Turnos de varias canchas en un rango de fechas (bloqueo y desbloqueo por rango). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.idCancha in :canchas and t.fecha between :desde and :hasta order by t.id")
    List<Turno> bloquearDeCanchasEntre(@Param("canchas") Collection<Long> idsCancha, @Param("desde") LocalDate desde,
                                       @Param("hasta") LocalDate hasta);
}
