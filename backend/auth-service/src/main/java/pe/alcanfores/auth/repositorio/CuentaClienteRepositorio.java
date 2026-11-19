package pe.alcanfores.auth.repositorio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.auth.modelo.CuentaCliente;

import java.util.Optional;

public interface CuentaClienteRepositorio extends JpaRepository<CuentaCliente, Long> {

    /** Login por DNI o por correo; bloquea la fila para no perder la cuenta de intentos fallidos simultáneos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaCliente c where c.dni = :usuario or c.correo = :usuario")
    Optional<CuentaCliente> buscarParaLogin(@Param("usuario") String usuario);

    boolean existsByDni(String dni);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdNot(String correo, Long id);
}
