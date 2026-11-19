package pe.alcanfores.reservas.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.alcanfores.reservas.modelo.Cliente;

import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByDni(String dni);

    /** Búsqueda por DNI, nombre, apellido o celular (q ya viene en minúsculas y con comodines). */
    @Query("select c from Cliente c where :q is null or c.dni like :q or c.telefono like :q "
            + "or lower(concat(c.nombres, ' ', c.apellidos)) like :q or lower(c.correo) like :q")
    Page<Cliente> buscar(@Param("q") String q, Pageable pagina);
}
