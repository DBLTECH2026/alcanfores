package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.catalogo.modelo.Deporte;
import pe.alcanfores.catalogo.modelo.EstadoCancha;

import java.math.BigDecimal;
import java.util.List;

/** Alta o edición de una cancha (panel, solo ADMIN). Reglas del SPEC §3 «Cancha». */
@PreciosCoherentes
public record CanchaSolicitud(
        @NotBlank(message = "Escribe el nombre de la cancha.")
        @Size(min = 3, max = 60, message = "El nombre debe tener entre 3 y 60 caracteres.")
        String nombre,

        @NotNull(message = "Elige el deporte de la cancha.")
        Deporte deporte,

        @NotBlank(message = "Indica la superficie (por ejemplo, grass sintético).")
        @Size(min = 3, max = 40, message = "La superficie debe tener entre 3 y 40 caracteres.")
        String superficie,

        @NotNull(message = "Indica cuántos jugadores entran en la cancha.")
        @Min(value = 2, message = "La capacidad va de 2 a 30 jugadores.")
        @Max(value = 30, message = "La capacidad va de 2 a 30 jugadores.")
        Integer capacidad,

        @NotBlank(message = "Escribe una descripción corta.")
        @Size(min = 10, max = 255, message = "La descripción corta debe tener entre 10 y 255 caracteres.")
        String descripcion,

        @Size(max = 2000, message = "La descripción larga admite hasta 2000 caracteres.")
        String descripcionLarga,

        @NotNull(message = "Indica si la cancha es techada.")
        Boolean techada,

        @NotNull(message = "Indica si la cancha tiene iluminación LED.")
        Boolean iluminacionLed,

        @Size(max = 40, message = "Las medidas admiten hasta 40 caracteres.")
        @Pattern(regexp = "^$|^\\d{1,3}([.,]\\d{1,2})? ?x ?\\d{1,3}([.,]\\d{1,2})? ?m$",
                message = "Escribe las medidas como «30 x 18 m».")
        String medidas,

        @Size(max = 12, message = "Agrega como máximo 12 características.")
        List<@NotBlank(message = "La característica no puede quedar vacía.")
             @Size(min = 2, max = 60, message = "Cada característica debe tener entre 2 y 60 caracteres.") String> caracteristicas,

        @NotNull(message = "Indica el precio de día.")
        @DecimalMin(value = "10.00", message = "El precio debe estar entre S/ 10.00 y S/ 999.00.")
        @DecimalMax(value = "999.00", message = "El precio debe estar entre S/ 10.00 y S/ 999.00.")
        @Digits(integer = 3, fraction = 2, message = "Usa como máximo 2 decimales.")
        BigDecimal precioDia,

        @NotNull(message = "Indica el precio de noche.")
        @DecimalMin(value = "10.00", message = "El precio debe estar entre S/ 10.00 y S/ 999.00.")
        @DecimalMax(value = "999.00", message = "El precio debe estar entre S/ 10.00 y S/ 999.00.")
        @Digits(integer = 3, fraction = 2, message = "Usa como máximo 2 decimales.")
        BigDecimal precioNoche,

        /** Opcional: al crear se usa ACTIVA; al editar se conserva el estado actual. */
        EstadoCancha estado,

        /** Opcional: posición en el portal (al crear va al final). */
        @Min(value = 1, message = "El orden va de 1 a 99.")
        @Max(value = 99, message = "El orden va de 1 a 99.")
        Integer orden
) {
}
