package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.comun.validacion.SinEnlaces;

/** «Califica tu experiencia»: el cliente se identifica con el código de su reserva y su DNI. */
public record ResenaSolicitud(
        @NotBlank(message = "Ingresa el código de tu reserva.")
        @CodigoReserva
        String codigoReserva,

        @NotBlank(message = "Ingresa tu DNI.")
        @Dni
        String dni,

        @NotNull(message = "Elige una calificación de 1 a 5 estrellas.")
        @Min(value = 1, message = "Elige una calificación de 1 a 5 estrellas.")
        @Max(value = 5, message = "Elige una calificación de 1 a 5 estrellas.")
        Integer puntuacion,

        @NotBlank(message = "Cuéntanos cómo te fue.")
        @Size(min = 10, max = 500, message = "Escribe entre 10 y 500 caracteres, sin enlaces.")
        @SinEnlaces
        String comentario
) {
}
