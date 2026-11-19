package pe.alcanfores.catalogo.cliente;

/**
 * Respuesta de reservas-service a {@code GET /interno/reservas/{codigo}/elegible-resena?dni&idCancha}.
 *
 * @param elegible true si la reserva es de esa cancha, el DNI coincide, está CONFIRMADA y ya se jugó (o tiene ASISTIO)
 * @param motivo   por qué no puede reseñar (mensaje listo para mostrar al cliente)
 * @param autor    nombre corto para la reseña, p. ej. «Diego R.»
 */
public record ElegibilidadResena(boolean elegible, String motivo, String autor) {
}
