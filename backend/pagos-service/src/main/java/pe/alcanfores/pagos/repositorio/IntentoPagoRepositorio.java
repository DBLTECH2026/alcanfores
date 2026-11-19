package pe.alcanfores.pagos.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.IntentoPago;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntentoPagoRepositorio extends JpaRepository<IntentoPago, Long> {

    Optional<IntentoPago> findByReferencia(String referencia);

    boolean existsByReferencia(String referencia);

    /** El webhook y el sondeo pueden llegar a la vez: se serializan sobre la fila del intento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntentoPago i where i.referencia = :referencia")
    Optional<IntentoPago> bloquear(@Param("referencia") String referencia);

    List<IntentoPago> findByEstadoInAndExpiraEnBefore(Collection<EstadoIntento> estados, LocalDateTime momento);

    List<IntentoPago> findByCodigoReservaAndEstadoIn(String codigoReserva, Collection<EstadoIntento> estados);
}
