package pe.alcanfores.pagos.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.pagos.modelo.Serie;

import java.util.Optional;

public interface SerieRepositorio extends JpaRepository<Serie, String> {

    /** {@code SELECT … FOR UPDATE}: dos emisiones simultáneas esperan su turno y nunca repiten ni saltan números. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Serie s where s.serie = :serie")
    Optional<Serie> bloquear(@Param("serie") String serie);
}
