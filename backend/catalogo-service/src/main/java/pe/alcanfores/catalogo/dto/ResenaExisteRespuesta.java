package pe.alcanfores.catalogo.dto;

/** {@code GET /interno/resenas/{codigoReserva}}: si la reserva ya tiene reseña (para {@code puedeResenar} en reservas). */
public record ResenaExisteRespuesta(String codigoReserva, boolean existe) {
}
