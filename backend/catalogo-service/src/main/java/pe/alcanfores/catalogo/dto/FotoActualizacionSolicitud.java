package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Cambios parciales de una foto: solo se aplica lo que llega (al menos un dato). */
public record FotoActualizacionSolicitud(
        @Size(max = 120, message = "La leyenda admite hasta 120 caracteres.")
        String leyenda,

        @Min(value = 1, message = "El orden va de 1 a 99.")
        @Max(value = 99, message = "El orden va de 1 a 99.")
        Integer orden,

        Boolean principal
) {
    public boolean vacia() {
        return leyenda == null && orden == null && principal == null;
    }
}
