package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.NotNull;
import pe.alcanfores.catalogo.modelo.EstadoResena;

public record EstadoResenaSolicitud(
        @NotNull(message = "Elige el estado: PUBLICADA u OCULTA.")
        EstadoResena estado
) {
}
