package pe.alcanfores.auth.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;

import java.util.Optional;

public interface AdministradorRepositorio extends JpaRepository<Administrador, Long>, JpaSpecificationExecutor<Administrador> {

    /** Bloquea la fila para que dos intentos simultáneos no pierdan la cuenta de fallos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Administrador a where a.usuario = :usuario")
    Optional<Administrador> buscarParaLogin(@Param("usuario") String usuario);

    /** Para recuperar la contraseña: sin bloqueo de fila (no cambia los intentos de login). */
    Optional<Administrador> findFirstByUsuarioOrCorreo(String usuario, String correo);

    boolean existsByUsuario(String usuario);

    boolean existsByCorreo(String correo);

    boolean existsByUsuarioAndIdNot(String usuario, Long id);

    boolean existsByCorreoAndIdNot(String correo, Long id);

    long countByRolAndEstado(Rol rol, EstadoUsuario estado);
}
