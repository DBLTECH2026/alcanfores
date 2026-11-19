package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.alcanfores.auth.validacion.ContrasenaSegura;
import pe.alcanfores.auth.validacion.ContrasenasCoinciden;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.comun.validacion.NombrePersona;

/** Alta de una cuenta de cliente desde el portal (mismos mensajes que la reserva, SPEC §3). */
@ContrasenasCoinciden
public record RegistroClienteSolicitud(
        @NotNull(message = Reglas.MENSAJE_DNI) @Dni String dni,
        @NotNull(message = Reglas.MENSAJE_NOMBRE) @NombrePersona String nombres,
        @NotNull(message = Reglas.MENSAJE_NOMBRE) @NombrePersona String apellidos,
        @NotBlank(message = Reglas.MENSAJE_CORREO_CLIENTE) @Size(max = 100, message = Reglas.MENSAJE_CORREO_CLIENTE)
        @Email(regexp = Reglas.CORREO, message = Reglas.MENSAJE_CORREO_CLIENTE) String correo,
        @NotNull(message = Reglas.MENSAJE_CELULAR) @Celular String telefono,
        @NotBlank(message = "Ingresa una contraseña.") @ContrasenaSegura String contrasena,
        @NotBlank(message = "Repite la contraseña.") String confirmacion,
        @NotNull(message = Reglas.MENSAJE_TERMINOS) @AssertTrue(message = Reglas.MENSAJE_TERMINOS) Boolean aceptaTerminos,
        @NotNull(message = Reglas.MENSAJE_TERMINOS) @AssertTrue(message = Reglas.MENSAJE_TERMINOS) Boolean aceptaPrivacidad)
        implements ContrasenasCoinciden.ConConfirmacion {

    @Override
    public String contrasenaAConfirmar() {
        return contrasena;
    }

    @Override
    public String toString() {
        return "RegistroClienteSolicitud[dni=" + dni + ", correo=" + correo + "]";
    }
}
