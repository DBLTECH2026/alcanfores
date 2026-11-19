package pe.alcanfores.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.catalogo.modelo.TipoDiaEspecial;

import java.time.LocalDate;
import java.util.List;

/** Calendario de feriados y días no laborables (SPEC_V2 §B). */
public final class CalendarioDtos {

    private CalendarioDtos() {
    }

    /** {@code POST/PUT /api/admin/calendario}. {@code canchas} solo en NO_LABORABLE (vacío o nulo = todas). */
    public record DiaEspecialSolicitud(
            @NotNull(message = "Elige la fecha.")
            LocalDate fecha,

            @NotNull(message = "Elige si es feriado o día no laborable.")
            TipoDiaEspecial tipo,

            @NotBlank(message = "Escribe el nombre o motivo del día.")
            @Size(min = 3, max = 120, message = "El nombre debe tener entre 3 y 120 caracteres.")
            @Pattern(regexp = "^(?!.*(?i:https?://|www\\.))[^<>]*$", message = "Escribe el nombre sin enlaces ni los signos < >.")
            String nombre,

            List<@NotNull(message = "Elige una cancha válida.") Long> canchas
    ) {
    }

    /** Ítem del calendario ({@code canchas} = null → todas). */
    public record DiaEspecialRespuesta(Long id, LocalDate fecha, String tipo, String nombre, List<Long> canchas) {
    }

    /** {@code POST /api/admin/calendario/feriados-peru}. */
    public record CargaFeriadosRespuesta(int creados, int existentes) {
    }
}
