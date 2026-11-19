package pe.alcanfores.catalogo.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.catalogo.modelo.Resena;

import java.util.List;

public interface ResenaRepositorio extends JpaRepository<Resena, Long> {

    boolean existsByCodigoReserva(String codigoReserva);

    Page<Resena> findByCanchaIdAndEstado(Long idCancha, EstadoResena estado, Pageable pageable);

    /** Conteo por puntuación de las reseñas publicadas de una cancha: filas {puntuacion, cantidad}. */
    @Query("select r.puntuacion, count(r) from Resena r where r.cancha.id = :idCancha and r.estado = :estado group by r.puntuacion")
    List<Object[]> contarPorPuntuacion(@Param("idCancha") Long idCancha, @Param("estado") EstadoResena estado);

    /** Suma de estrellas y total de reseñas publicadas por cancha: filas {idCancha, suma, total}. */
    @Query("select r.cancha.id, sum(r.puntuacion), count(r) from Resena r where r.estado = :estado group by r.cancha.id")
    List<Object[]> calificacionPorCancha(@Param("estado") EstadoResena estado);

    @EntityGraph(attributePaths = "cancha")
    @Query("""
            select r from Resena r
            where (:estado is null or r.estado = :estado)
              and (:idCancha is null or r.cancha.id = :idCancha)
              and (:puntuacion is null or r.puntuacion = :puntuacion)
            """)
    Page<Resena> buscar(@Param("estado") EstadoResena estado, @Param("idCancha") Long idCancha,
                        @Param("puntuacion") Integer puntuacion, Pageable pageable);
}
