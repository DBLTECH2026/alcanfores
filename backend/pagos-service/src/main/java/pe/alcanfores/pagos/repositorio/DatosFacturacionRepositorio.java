package pe.alcanfores.pagos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.pagos.modelo.DatosFacturacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DatosFacturacionRepositorio extends JpaRepository<DatosFacturacion, Long> {

    Optional<DatosFacturacion> findByIdPago(Long idPago);

    Optional<DatosFacturacion> findFirstByReferenciaIntentoOrderByIdDesc(String referenciaIntento);

    /** La última facturación pedida para la reserva (la hereda un reenvío o un cobro que no trae {@code comprobante}). */
    Optional<DatosFacturacion> findFirstByCodigoReservaOrderByIdDesc(String codigoReserva);

    List<DatosFacturacion> findByIdPagoIn(Collection<Long> idsPago);
}
