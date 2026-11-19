package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.NotNull;
import pe.alcanfores.catalogo.modelo.EstadoCancha;

public record EstadoCanchaSolicitud(
        @NotNull(message = "Elige el estado: ACTIVA o INACTIVA.")
        EstadoCancha estado
) {
}
