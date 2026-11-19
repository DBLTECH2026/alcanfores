package pe.alcanfores.catalogo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.catalogo.modelo.CanchaFoto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CanchaFotoRepositorio extends JpaRepository<CanchaFoto, Long> {

    List<CanchaFoto> findByIdCanchaOrderByOrdenAscIdAsc(Long idCancha);

    List<CanchaFoto> findByIdCanchaInAndPrincipalTrue(Collection<Long> idsCancha);

    Optional<CanchaFoto> findByIdAndIdCancha(Long id, Long idCancha);

    Optional<CanchaFoto> findByArchivo(String archivo);

    long countByIdCancha(Long idCancha);

    @Query("select coalesce(max(f.orden), 0) from CanchaFoto f where f.idCancha = :idCancha")
    int maximoOrden(@Param("idCancha") Long idCancha);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CanchaFoto f set f.principal = false where f.idCancha = :idCancha and f.id <> :idFoto")
    void quitarPrincipalExcepto(@Param("idCancha") Long idCancha, @Param("idFoto") Long idFoto);
}
