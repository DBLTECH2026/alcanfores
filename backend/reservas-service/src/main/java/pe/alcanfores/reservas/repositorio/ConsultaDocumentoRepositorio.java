package pe.alcanfores.reservas.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.reservas.modelo.ConsultaDocumento;

public interface ConsultaDocumentoRepositorio extends JpaRepository<ConsultaDocumento, ConsultaDocumento.Clave> {
}
