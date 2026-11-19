package pe.alcanfores.pagos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.pagos.modelo.DatosFacturacion;

import java.util.Optional;

public interface DatosFacturacionRepositorio extends JpaRepository<DatosFacturacion, Long> {

    Optional<DatosFacturacion> findByIdPago(Long idPago);

    Optional<DatosFacturacion> findFirstByReferenciaIntentoOrderByIdDesc(String referenciaIntento);
}
