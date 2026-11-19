package pe.alcanfores.pagos.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PagoRepositorio extends JpaRepository<Pago, Long>, JpaSpecificationExecutor<Pago> {

    List<Pago> findByCodigoReservaOrderByFechaEnvioAsc(String codigoReserva);

    List<Pago> findByCodigoReservaAndEstadoIn(String codigoReserva, Collection<EstadoValidacion> estados);

    boolean existsByCodigoReservaAndEstado(String codigoReserva, EstadoValidacion estado);

    /** Un mismo número de operación de Yape/Plin no puede sustentar dos pagos. */
    boolean existsByMetodoAndCodigoOperacionAndEstadoIn(MetodoPago metodo, String codigoOperacion,
                                                        Collection<EstadoValidacion> estados);

    /** Lo ya cobrado de una reserva según pagos-service (dueño de los pagos). */
    @Query("select coalesce(sum(p.monto), 0) from Pago p where p.codigoReserva = :codigo and p.estado = :estado")
    BigDecimal sumarPorEstado(@Param("codigo") String codigoReserva, @Param("estado") EstadoValidacion estado);

    /** Bloquea la fila del pago para validarlo o rechazarlo sin carreras entre dos recepcionistas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.id = :id")
    Optional<Pago> bloquear(@Param("id") Long id);

    /**
     * Todos los pagos de una reserva, bloqueados y en orden: un consumidor de eventos espera a una validación del panel
     * en curso y no devuelve a PENDIENTE un pago que se acaba de validar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.codigoReserva = :codigo order by p.id")
    List<Pago> bloquearDeReserva(@Param("codigo") String codigoReserva);

    List<Pago> findByEstadoAndFechaValidacionBetween(EstadoValidacion estado, LocalDateTime desde, LocalDateTime hasta);

    @Query("select p.estado, count(p) from Pago p group by p.estado")
    List<Object[]> contarPorEstado();
}
