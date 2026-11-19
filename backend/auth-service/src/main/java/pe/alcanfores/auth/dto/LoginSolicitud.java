package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso. El usuario se compara sin espacios y en minúsculas; aquí no se aplica el patrón de
 * usuario para no dar pistas de qué cuentas existen (cualquier error de credencial responde igual).
 */
public record LoginSolicitud(
        @NotBlank(message = "Ingresa tu usuario.")
        @Size(max = 60, message = "El usuario tiene como máximo 30 caracteres.")
        String usuario,

        @NotBlank(message = "Ingresa tu contraseña.")
        @Size(max = 128, message = "La contraseña tiene como máximo 64 caracteres.")
        String contrasena) {

    @Override
    public String toString() {
        // Nunca imprimir la contraseña en logs.
        return "LoginSolicitud[usuario=" + usuario + "]";
    }
}
