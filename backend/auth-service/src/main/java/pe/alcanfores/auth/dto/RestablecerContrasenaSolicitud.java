package pe.alcanfores.auth.dto;

import jakarta.validation.constraints.NotBlank;
import pe.alcanfores.auth.validacion.ContrasenaSegura;

/** Un ADMIN asigna una contraseña nueva a otro usuario (también lo desbloquea). */
public record RestablecerContrasenaSolicitud(
        @NotBlank(message = "Ingresa la contraseña nueva.")
        @ContrasenaSegura
        String nueva) {

    @Override
    public String toString() {
        return "RestablecerContrasenaSolicitud[***]";
    }
}
