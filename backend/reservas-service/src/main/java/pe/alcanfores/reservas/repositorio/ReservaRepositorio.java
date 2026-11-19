package pe.alcanfores.reservas.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepositorio extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    @EntityGraph(attributePaths = {"turno", "cliente"})
    Optional<Reserva> findByCodigoReserva(String codigoReserva);

    @Override
    @EntityGraph(attributePaths = {"turno", "cliente"})
    Page<Reserva> findAll(Specification<Reserva> filtro, Pageable pagina);

    boolean existsByCodigoReserva(String codigoReserva);

    /** Reservas cuyo plazo para pagar ya venció (las expira la tarea programada). */
    @Query("select r.id from Reserva r where r.estado = :estado and r.estadoPago in :estadosPago and r.venceEn < :ahora")
    List<Long> idsVencidas(@Param("estado") EstadoReserva estado, @Param("estadosPago") Collection<EstadoPago> estadosPago,
                           @Param("ahora") LocalDateTime ahora);

    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query("select r from Reserva r where r.turno.fecha between :desde and :hasta")
    List<Reserva> conTurnoEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query("select r from Reserva r where r.estado in :estados and (r.turno.fecha > :fecha "
            + "or (r.turno.fecha = :fecha and r.turno.horaInicio >= :hora)) order by r.turno.fecha, r.turno.horaInicio")
    List<Reserva> proximasDesde(@Param("estados") Collection<EstadoReserva> estados, @Param("fecha") LocalDate fecha,
                                @Param("hora") LocalTime hora, Pageable pagina);

    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query("select r from Reserva r where r.estado = :estado and r.estadoPago in :estadosPago order by r.fechaRegistro")
    List<Reserva> enEstadoYPago(@Param("estado") EstadoReserva estado, @Param("estadosPago") Collection<EstadoPago> estadosPago);

    long countByEstadoAndEstadoPago(EstadoReserva estado, EstadoPago estadoPago);

    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query("select r from Reserva r where r.cliente.id in :ids order by r.turno.fecha desc, r.turno.horaInicio desc")
    List<Reserva> deClientes(@Param("ids") Collection<Long> idsClientes);

    /** «Mis reservas»: todas las del DNI (estado opcional). */
    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query(value = "select r from Reserva r where r.cliente.dni = :dni and (:estado is null or r.estado = :estado)",
            countQuery = "select count(r) from Reserva r where r.cliente.dni = :dni and (:estado is null or r.estado = :estado)")
    Page<Reserva> deDni(@Param("dni") String dni, @Param("estado") EstadoReserva estado, Pageable pagina);

    @EntityGraph(attributePaths = {"turno", "cliente"})
    @Query("select r from Reserva r where r.esSemilla = true order by r.fechaRegistro")
    List<Reserva> sembradas();
}
