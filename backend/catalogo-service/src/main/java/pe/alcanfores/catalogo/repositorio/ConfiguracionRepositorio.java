package pe.alcanfores.catalogo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.catalogo.modelo.Configuracion;

public interface ConfiguracionRepositorio extends JpaRepository<Configuracion, String> {
}
