package pe.alcanfores.catalogo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.alcanfores.catalogo.modelo.FeriadoModo;

import java.math.BigDecimal;
import java.util.List;

/** Horario propio de cada cancha (SPEC_V2 §A): solicitud del panel, respuestas y vista previa de turnos. */
public final class HorarioDtos {

    /** «HH:mm» de 00:00 a 23:59, o «24:00» (fin del día, solo para el cierre). */
    static final String HORA = "^(([01]\\d|2[0-3]):[0-5]\\d|24:00)$";
    static final String MENSAJE_HORA = "Escribe la hora como HH:mm (por ejemplo 18:00).";

    private HorarioDtos() {
    }

    /** {@code PUT /api/admin/canchas/{id}/horario} (y cuerpo de la vista previa por POST). */
    public record HorarioSolicitud(
            @NotNull(message = "Elige la duración del turno.")
            @Min(value = 30, message = "La duración del turno va de 30 a 240 minutos, en múltiplos de 15.")
            @Max(value = 240, message = "La duración del turno va de 30 a 240 minutos, en múltiplos de 15.")
            Integer duracionTurnoMin,

            @NotBlank(message = "Indica desde qué hora se cobra la tarifa de noche.")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = MENSAJE_HORA)
            String inicioNoche,

            @NotNull(message = "Envía los 7 días de la semana, de lunes (1) a domingo (7), sin repetir.")
            @Size(min = 7, max = 7, message = "Envía los 7 días de la semana, de lunes (1) a domingo (7), sin repetir.")
            List<@NotNull(message = "Completa el horario del día.") @Valid DiaSolicitud> dias,

            @NotNull(message = "Indica qué hace la cancha en los feriados.")
            @Valid
            FeriadoSolicitud feriado
    ) {
    }

    public record DiaSolicitud(
            @NotNull(message = "Indica el día de la semana (1 = lunes … 7 = domingo).")
            @Min(value = 1, message = "Indica el día de la semana (1 = lunes … 7 = domingo).")
            @Max(value = 7, message = "Indica el día de la semana (1 = lunes … 7 = domingo).")
            Integer diaSemana,

            @NotNull(message = "Indica si la cancha abre ese día.")
            Boolean abierto,

            @Pattern(regexp = HORA, message = MENSAJE_HORA)
            String apertura,

            @Pattern(regexp = HORA, message = MENSAJE_HORA)
            String cierre
    ) {
    }

    public record FeriadoSolicitud(
            @NotNull(message = "Elige si la cancha abre o cierra en los feriados.")
            FeriadoModo modo,

            @NotNull(message = "Indica el adicional por turno en feriado (0 si no cobras extra).")
            @DecimalMin(value = "0.00", message = "El adicional va de S/ 0.00 a S/ 999.99.")
            @DecimalMax(value = "999.99", message = "El adicional va de S/ 0.00 a S/ 999.99.")
            @Digits(integer = 3, fraction = 2, message = "Usa como máximo 2 decimales.")
            BigDecimal recargo
    ) {
    }

    /** Horario en el detalle de la cancha (portal y panel). {@code texto}: «Lun a vie 8:00 a. m.–11:00 p. m. · …». */
    public record HorarioRespuesta(Integer duracionTurnoMin, String inicioNoche, List<DiaRespuesta> dias, String texto) {
    }

    public record DiaRespuesta(int diaSemana, boolean abierto, String apertura, String cierre) {
    }

    public record FeriadoRespuesta(String modo, BigDecimal recargo) {
    }

    /** {@code GET/PUT /api/admin/canchas/{id}/horario}. */
    public record HorarioCanchaRespuesta(Integer duracionTurnoMin, String inicioNoche, List<DiaRespuesta> dias, String texto,
                                         FeriadoRespuesta feriado) {
    }

    /** Turno que saldría con ese horario (sin recargo de feriado). */
    public record TurnoVistaPrevia(String horaInicio, String horaFin, BigDecimal precio, String franja) {
    }
}
