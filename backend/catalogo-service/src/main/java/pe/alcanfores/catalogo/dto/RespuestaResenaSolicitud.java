package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.SinEnlaces;

public record RespuestaResenaSolicitud(
        @NotBlank(message = "Escribe la respuesta para el cliente.")
        @Size(min = 10, max = 500, message = "La respuesta debe tener entre 10 y 500 caracteres.")
        @SinEnlaces(message = "La respuesta no puede tener enlaces ni estar escrita toda en mayúsculas.")
        String respuesta
) {
}
