package pe.alcanfores.catalogo.cliente;

/** Consultas síncronas a reservas-service (endpoints {@code /interno/**}). */
public interface ReservasCliente {

    /**
     * Pregunta si la reserva habilita a dejar una reseña en la cancha.
     *
     * @throws pe.alcanfores.comun.error.NegocioExcepcion 503 {@code SERVICIO_NO_DISPONIBLE} si reservas no responde
     */
    ElegibilidadResena elegibleResena(String codigoReserva, String dni, Long idCancha);
}
