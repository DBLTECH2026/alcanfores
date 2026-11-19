package pe.alcanfores.notificaciones.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.modelo.Recordatorio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecordatorioRepositorio extends JpaRepository<Recordatorio, Long> {

    Optional<Recordatorio> findByCodigoReserva(String codigoReserva);

    Page<Recordatorio> findByEstado(EstadoRecordatorio estado, Pageable pageable);

    List<Recordatorio> findByEstadoAndEnviarEnLessThanEqualOrderByEnviarEnAsc(EstadoRecordatorio estado, LocalDateTime limite);

    /** Marca ENVIADO solo si seguía PROGRAMADO: así dos instancias del job no envían el mismo recordatorio. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Recordatorio r set r.estado = pe.alcanfores.notificaciones.modelo.EstadoRecordatorio.ENVIADO, "
            + "r.enviadoEn = :ahora where r.id = :id "
            + "and r.estado = pe.alcanfores.notificaciones.modelo.EstadoRecordatorio.PROGRAMADO")
    int tomarParaEnvio(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);
}
