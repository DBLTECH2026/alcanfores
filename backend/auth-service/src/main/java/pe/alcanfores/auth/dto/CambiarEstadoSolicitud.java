package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.NotNull;
import pe.alcanfores.auth.modelo.EstadoUsuario;

/** {@code {estado: "ACTIVO" | "INACTIVO"}} */
public record CambiarEstadoSolicitud(@NotNull(message = "Elige un estado: ACTIVO o INACTIVO.") EstadoUsuario estado) {
}
