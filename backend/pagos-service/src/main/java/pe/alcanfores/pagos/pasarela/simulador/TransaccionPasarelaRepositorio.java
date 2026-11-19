package pe.alcanfores.pagos.pasarela.simulador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransaccionPasarelaRepositorio extends JpaRepository<TransaccionPasarela, Long> {

    Optional<TransaccionPasarela> findFirstByReferenciaAndEstadoOrderByIdDesc(String referencia,
                                                                              TransaccionPasarela.Estado estado);

    /** Avisos al comercio que fallaron y toca reintentar. */
    List<TransaccionPasarela> findTop20ByNotificadaFalseAndIdEventoIsNotNullAndIntentosNotificacionLessThanAndProximoIntentoBeforeOrderByIdAsc(
            int maxIntentos, LocalDateTime momento);
}
