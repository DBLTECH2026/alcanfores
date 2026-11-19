package pe.alcanfores.notificaciones.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.notificaciones.modelo.ConfigCorreo;

public interface ConfigCorreoRepositorio extends JpaRepository<ConfigCorreo, Byte> {
}
