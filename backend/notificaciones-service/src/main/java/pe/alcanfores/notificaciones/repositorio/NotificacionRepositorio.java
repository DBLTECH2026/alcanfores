package pe.alcanfores.notificaciones.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacionRepositorio extends JpaRepository<Notificacion, Long>, JpaSpecificationExecutor<Notificacion> {

    List<Notificacion> findByCodigoReservaOrderByIdAsc(String codigoReserva);

    /**
     * FALLIDOS que aún tienen intentos y PENDIENTES que quedaron colgados (el proceso se cayó a mitad del envío).
     * Los mensajes de prueba del panel y los códigos de verificación ({@code sinReintento}) no se reintentan solos.
     */
    @Query("""
            select n.id from Notificacion n
            where n.evento not in :sinReintento
              and ((n.estado = pe.alcanfores.notificaciones.modelo.EstadoNotificacion.FALLIDO and n.intentos < :maxIntentos)
               or (n.estado = pe.alcanfores.notificaciones.modelo.EstadoNotificacion.PENDIENTE and n.creadoEn < :colgadoAntesDe))
            order by n.id""")
    List<Long> idsPorReintentar(@Param("maxIntentos") int maxIntentos, @Param("colgadoAntesDe") LocalDateTime colgadoAntesDe,
                                @Param("sinReintento") List<String> sinReintento);

    /**
     * Toma el aviso para reenviarlo solo si sigue en el estado esperado (si hay dos instancias, solo una lo envía).
     * @return 1 si esta instancia lo tomó.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Notificacion n set n.estado = pe.alcanfores.notificaciones.modelo.EstadoNotificacion.PENDIENTE "
            + "where n.id = :id and n.estado = :esperado")
    int tomarParaEnvio(@Param("id") Long id, @Param("esperado") EstadoNotificacion esperado);

    long countByEstado(EstadoNotificacion estado);
}
