package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.SinEnlaces;

import java.time.LocalDate;

/** Reseña que carga el complejo desde la ficha de la cancha (sin reserva): autor, estrellas, comentario y fecha. */
public record ResenaComplejoSolicitud(
        @NotBlank(message = "Escribe el nombre de quien opina (por ejemplo, «Diego R.»).")
        @Size(min = 2, max = 60, message = "El autor debe tener entre 2 y 60 letras.")
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' .-]+$", message = "Usa solo letras, espacios y puntos en el autor.")
        String autor,

        @NotNull(message = "Elige una calificación de 1 a 5 estrellas.")
        @Min(value = 1, message = "Elige una calificación de 1 a 5 estrellas.")
        @Max(value = 5, message = "Elige una calificación de 1 a 5 estrellas.")
        Integer puntuacion,

        @NotBlank(message = "Escribe el comentario.")
        @Size(min = 10, max = 500, message = "Escribe entre 10 y 500 caracteres, sin enlaces.")
        @SinEnlaces
        String comentario,

        @NotNull(message = "Elige la fecha de la reseña.")
        LocalDate fecha
) {
}
