package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.NombrePersona;

/** El cliente edita sus datos; el DNI no se cambia (es el vínculo con sus reservas). */
public record EditarClienteSolicitud(
        @NotNull(message = Reglas.MENSAJE_NOMBRE) @NombrePersona String nombres,
        @NotNull(message = Reglas.MENSAJE_NOMBRE) @NombrePersona String apellidos,
        @NotBlank(message = Reglas.MENSAJE_CORREO_CLIENTE) @Size(max = 100, message = Reglas.MENSAJE_CORREO_CLIENTE)
        @Email(regexp = Reglas.CORREO, message = Reglas.MENSAJE_CORREO_CLIENTE) String correo,
        @NotNull(message = Reglas.MENSAJE_CELULAR) @Celular String telefono) {
}
