package pe.alcanfores.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.Celular;

/** Mensaje de prueba desde el panel: celular peruano y texto de 1 a 1000 caracteres. */
public record PruebaWhatsappSolicitud(
        @NotBlank(message = "Ingresa un celular peruano de 9 dígitos que empiece con 9.")
        @Celular
        String telefono,
        @NotBlank(message = "Escribe el mensaje de prueba (1 a 1000 caracteres).")
        @Size(max = 1000, message = "Escribe el mensaje de prueba (1 a 1000 caracteres).")
        String mensaje) {
}
