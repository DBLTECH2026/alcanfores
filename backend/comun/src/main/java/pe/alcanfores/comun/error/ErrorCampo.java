package pe.alcanfores.comun.error;

/** Error de validación asociado a un campo del formulario (ej. {@code cliente.dni}). */
public record ErrorCampo(String campo, String mensaje) {
}
