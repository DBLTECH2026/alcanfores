package pe.alcanfores.pagos.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.alcanfores.pagos.modelo.Comprobante;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComprobanteRepositorio extends JpaRepository<Comprobante, Long>, JpaSpecificationExecutor<Comprobante> {

    /** Evita dos notas de crédito simultáneas sobre el mismo comprobante. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comprobante c where c.id = :id")
    Optional<Comprobante> bloquear(@Param("id") Long id);

    List<Comprobante> findByIdPagoOrderByIdAsc(Long idPago);

    List<Comprobante> findByIdPagoInOrderByIdAsc(Collection<Long> idsPago);

    Optional<Comprobante> findBySerieAndCorrelativo(String serie, int correlativo);

    List<Comprobante> findByFechaEmisionBetween(LocalDateTime desde, LocalDateTime hasta);
}
