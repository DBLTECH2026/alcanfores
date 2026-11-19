package pe.alcanfores.auth.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.auth.modelo.CodigoRecuperacion;
import pe.alcanfores.auth.modelo.CodigoRecuperacion.TipoCuenta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoRecuperacionRepositorio extends JpaRepository<CodigoRecuperacion, Long> {

    /** Envíos de la cuenta desde {@code desde}, del más reciente al más antiguo (límite de 3 cada 15 minutos). */
    List<CodigoRecuperacion> findByTipoCuentaAndIdCuentaAndCreadoEnAfterOrderByCreadoEnDesc(TipoCuenta tipo, Long idCuenta,
                                                                                            LocalDateTime desde);

    /** El último código de la cuenta, bloqueado para que dos intentos simultáneos no pierdan la cuenta de fallos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodigoRecuperacion c where c.tipoCuenta = :tipo and c.idCuenta = :id order by c.id desc limit 1")
    Optional<CodigoRecuperacion> ultimoParaVerificar(@Param("tipo") TipoCuenta tipo, @Param("id") Long idCuenta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoRecuperacion> findByTokenHash(String tokenHash);

    /** Solo vale el último código: los anteriores (pendientes o verificados) quedan ANULADOS. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoRecuperacion c set c.estado = pe.alcanfores.auth.modelo.CodigoRecuperacion.Estado.ANULADO "
            + "where c.tipoCuenta = :tipo and c.idCuenta = :id "
            + "and c.estado in (pe.alcanfores.auth.modelo.CodigoRecuperacion.Estado.PENDIENTE, "
            + "pe.alcanfores.auth.modelo.CodigoRecuperacion.Estado.VERIFICADO)")
    int anularVigentes(@Param("tipo") TipoCuenta tipo, @Param("id") Long idCuenta);
}
