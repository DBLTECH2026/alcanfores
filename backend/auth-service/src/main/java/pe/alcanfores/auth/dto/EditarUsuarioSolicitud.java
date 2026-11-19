package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.NombrePersona;

/** Edición de datos y rol de un usuario (la contraseña se cambia aparte). */
public record EditarUsuarioSolicitud(
        @NotBlank(message = "Ingresa los nombres.")
        @NombrePersona
        String nombres,

        @NotBlank(message = "Ingresa los apellidos.")
        @NombrePersona
        String apellidos,

        @NotBlank(message = "Ingresa el usuario.")
        @Pattern(regexp = Reglas.USUARIO, message = Reglas.MENSAJE_USUARIO)
        String usuario,

        @NotBlank(message = "Ingresa el correo.")
        @Size(max = 100, message = Reglas.MENSAJE_CORREO)
        @Email(regexp = Reglas.CORREO, message = Reglas.MENSAJE_CORREO)
        String correo,

        @NotNull(message = "Elige un rol.")
        Rol rol,

        /** Opcional (vacío = sin celular). */
        @Celular
        String telefono) {

    public EditarUsuarioSolicitud {
        telefono = Reglas.telefonoOpcional(telefono);
    }
}
