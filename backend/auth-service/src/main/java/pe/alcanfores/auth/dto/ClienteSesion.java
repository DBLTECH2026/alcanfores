package pe.alcanfores.auth.dto;

/** Datos de la cuenta del cliente que el portal muestra y usa para llenar la reserva. */
public record ClienteSesion(Long id, String dni, String nombres, String apellidos, String correo, String telefono) {
}
