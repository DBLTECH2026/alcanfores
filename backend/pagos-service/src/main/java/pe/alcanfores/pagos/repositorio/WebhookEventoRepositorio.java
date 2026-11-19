package pe.alcanfores.pagos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.alcanfores.pagos.modelo.ProveedorPasarela;
import pe.alcanfores.pagos.modelo.WebhookEvento;

public interface WebhookEventoRepositorio extends JpaRepository<WebhookEvento, Long> {

    boolean existsByProveedorAndIdExterno(ProveedorPasarela proveedor, String idExterno);
}
