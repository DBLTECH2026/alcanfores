package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.alcanfores.auth.validacion.ContrasenaSegura;
import pe.alcanfores.auth.validacion.ContrasenasCoinciden;

/** Cambio de contraseña del propio usuario: {@code {actual, nueva, confirmacion}}. */
@ContrasenasCoinciden
public record CambiarContrasenaSolicitud(
        @NotBlank(message = "Ingresa tu contraseña actual.")
        @Size(max = 128, message = "La contraseña actual no es correcta.")
        String actual,

        @NotBlank(message = "Ingresa la contraseña nueva.")
        @ContrasenaSegura
        String nueva,

        @NotBlank(message = "Repite la contraseña nueva.")
        String confirmacion) {

    @Override
    public String toString() {
        return "CambiarContrasenaSolicitud[***]";
    }
}
