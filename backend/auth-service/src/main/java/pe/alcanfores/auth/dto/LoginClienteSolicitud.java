package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ingreso del cliente con su DNI o su correo. Sin patrón: cualquier error de credencial responde igual. */
public record LoginClienteSolicitud(
        @NotBlank(message = "Ingresa tu DNI o correo.")
        @Size(max = 100, message = "Ingresa tu DNI o correo.")
        String usuario,

        @NotBlank(message = "Ingresa tu contraseña.")
        @Size(max = 128, message = "La contraseña tiene como máximo 64 caracteres.")
        String contrasena) {

    @Override
    public String toString() {
        return "LoginClienteSolicitud[usuario=" + usuario + "]";
    }
}
