package pe.alcanfores.auth.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.alcanfores.auth.modelo.Bitacora;

import java.util.List;

public interface BitacoraRepositorio extends JpaRepository<Bitacora, Long>, JpaSpecificationExecutor<Bitacora> {

    @Query("select distinct b.servicio from Bitacora b order by b.servicio")
    List<String> servicios();

    @Query("select distinct b.accion from Bitacora b order by b.accion")
    List<String> acciones();
}
