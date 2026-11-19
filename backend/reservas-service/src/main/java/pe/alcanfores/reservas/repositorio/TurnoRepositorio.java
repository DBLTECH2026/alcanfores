package pe.alcanfores.reservas.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.Turno;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TurnoRepositorio extends JpaRepository<Turno, Long> {

    /** {@code SELECT … FOR UPDATE}: bloquea el turno hasta el fin de la transacción (dos clientes no lo toman a la vez). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.id = :id")
    Optional<Turno> bloquearPorId(@Param("id") Long id);

    List<Turno> findByFechaOrderByIdCanchaAscHoraInicioAsc(LocalDate fecha);

    List<Turno> findByFechaBetweenOrderByFechaAscIdCanchaAscHoraInicioAsc(LocalDate desde, LocalDate hasta);

    /** Turnos de una cancha desde una fecha, bloqueados para actualizarlos sin pisar una reserva concurrente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.idCancha = :idCancha and t.fecha >= :desde")
    List<Turno> bloquearDeCanchaDesde(@Param("idCancha") Long idCancha, @Param("desde") LocalDate desde);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.idCancha = :idCancha and t.fecha = :fecha and t.horaInicio >= :desde "
            + "and t.horaInicio < :hasta order by t.horaInicio")
    List<Turno> bloquearRango(@Param("idCancha") Long idCancha, @Param("fecha") LocalDate fecha,
                              @Param("desde") LocalTime desde, @Param("hasta") LocalTime hasta);

    /** (idCancha, fecha, horaInicio) de los turnos ya existentes en un rango (para generar sin duplicar). */
    @Query("select t.idCancha, t.fecha, t.horaInicio from Turno t where t.fecha between :desde and :hasta")
    List<Object[]> clavesEnRango(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
