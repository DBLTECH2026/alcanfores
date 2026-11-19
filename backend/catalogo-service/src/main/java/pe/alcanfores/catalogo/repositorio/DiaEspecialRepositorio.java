package pe.alcanfores.catalogo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.catalogo.modelo.DiaEspecial;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaEspecialRepositorio extends JpaRepository<DiaEspecial, Long> {

    List<DiaEspecial> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    Optional<DiaEspecial> findByFecha(LocalDate fecha);
}
