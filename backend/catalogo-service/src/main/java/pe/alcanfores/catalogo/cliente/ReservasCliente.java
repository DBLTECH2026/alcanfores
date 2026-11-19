package pe.alcanfores.catalogo.cliente;

import java.time.LocalDate;
import java.util.List;

/** Consultas síncronas a reservas-service (endpoints {@code /interno/**}). */
public interface ReservasCliente {

    /**
     * Pregunta si la reserva habilita a dejar una reseña en la cancha.
     *
     * @throws pe.alcanfores.comun.error.NegocioExcepcion 503 {@code SERVICIO_NO_DISPONIBLE} si reservas no responde
     */
    ElegibilidadResena elegibleResena(String codigoReserva, String dni, Long idCancha);

    /**
     * Códigos de las reservas vigentes (PENDIENTE o CONFIRMADA) con algún turno en esa fecha y en esas canchas
     * ({@code idsCancha} nulo = todas).
     *
     * @throws pe.alcanfores.comun.error.NegocioExcepcion 503 {@code SERVICIO_NO_DISPONIBLE} si reservas no responde
     */
    List<String> reservasVigentes(LocalDate fecha, List<Long> idsCancha);
}
