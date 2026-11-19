package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.Celular;

/**
 * El usuario del panel completa su celular (opcional; vacío = quitarlo) y su correo. Con celular puede recibir por
 * WhatsApp el código para cambiar la contraseña.
 */
public record EditarMisDatosSolicitud(
        @Celular String telefono,
        @NotBlank(message = "Ingresa el correo.")
        @Size(max = 100, message = Reglas.MENSAJE_CORREO)
        @Email(regexp = Reglas.CORREO, message = Reglas.MENSAJE_CORREO)
        String correo) {

    public EditarMisDatosSolicitud {
        telefono = Reglas.telefonoOpcional(telefono);
    }
}
