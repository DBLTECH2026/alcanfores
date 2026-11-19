package pe.alcanfores.catalogo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.EstadoCancha;
import pe.alcanfores.catalogo.modelo.FeriadoModo;

import java.util.List;
import java.util.Optional;

public interface CanchaRepositorio extends JpaRepository<Cancha, Long> {

    List<Cancha> findAllByOrderByOrdenAscIdAsc();

    List<Cancha> findByEstadoOrderByOrdenAscIdAsc(EstadoCancha estado);

    Optional<Cancha> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** La columna usa collation utf8mb4_unicode_ci: «cancha 1» y «Cancha 1» chocan igual que en el índice único. */
    Optional<Cancha> findByNombreIgnoreCase(String nombre);

    List<Cancha> findByFeriadoModo(FeriadoModo modo);

    @Query("select coalesce(max(c.orden), 0) from Cancha c")
    int maximoOrden();
}
