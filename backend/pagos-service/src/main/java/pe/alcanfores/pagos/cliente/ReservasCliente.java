package pe.alcanfores.pagos.cliente;

import java.util.List;
import java.util.Optional;

/** Consultas síncronas a reservas-service (rutas {@code /interno/**}). */
public interface ReservasCliente {

    /**
     * @return la reserva o vacío si no existe.
     * @throws pe.alcanfores.comun.error.NegocioExcepcion 503 si reservas-service no responde.
     */
    Optional<ReservaInterna> buscar(String codigoReserva);

    /** Reservas de ejemplo para crear pagos coherentes en la semilla. */
    List<ReservaSemilla> paraSemilla();
}
