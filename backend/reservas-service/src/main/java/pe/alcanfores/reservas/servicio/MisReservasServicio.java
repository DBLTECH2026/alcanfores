package pe.alcanfores.reservas.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteItem;

/**
 * «Mis reservas» del cliente con cuenta: TODAS las reservas de su DNI (el DNI del token), incluidas las que hizo antes de
 * crear la cuenta.
 */
public interface MisReservasServicio {

    /** {@code estado}: PENDIENTE | CONFIRMADA | CANCELADA o nulo (todas). Más recientes primero. */
    PaginaRespuesta<ReservaClienteItem> listar(String dni, String estado, Pageable pagina);

    /** Mismo detalle que la consulta pública con código + DNI (404 si la reserva no es de ese DNI). */
    ReservaClienteDetalle detalle(String dni, String codigo);
}
