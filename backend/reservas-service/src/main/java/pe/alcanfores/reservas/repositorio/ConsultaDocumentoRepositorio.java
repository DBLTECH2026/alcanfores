package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.reservas.modelo.ConsultaDocumento;

public interface ConsultaDocumentoRepositorio extends JpaRepository<ConsultaDocumento, ConsultaDocumento.Clave> {

    /** ¿Hay una consulta con datos reales (no la caché de «no encontrado»)? */
    boolean existsByClaveAndFuenteNot(ConsultaDocumento.Clave clave, String fuente);
}
