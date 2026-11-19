package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * cancha.actualizada · la publica catálogo al crear o editar una cancha, cambiar su estado o su horario. Desde la v2 lleva el
 * horario propio de la cancha: duración del turno, inicio de la tarifa de noche, los 7 días y qué hace en feriado
 * ({@code feriadoModo} ABIERTA | CERRADA, {@code recargoFeriado} en soles por turno). Los campos de horario pueden llegar
 * nulos (mensajes de la v1): el consumidor usa entonces los valores por defecto.
 */
public record CanchaActualizadaEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idCancha, String nombre, String deporte,
                                      BigDecimal precioDia, BigDecimal precioNoche, String estado, Integer duracionTurnoMin,
                                      LocalTime inicioNoche, List<HorarioDia> dias, String feriadoModo,
                                      BigDecimal recargoFeriado) implements EventoDominio {

    /** Solo datos básicos (sin horario). */
    public CanchaActualizadaEvento(UUID idEvento, LocalDateTime ocurridoEn, Long idCancha, String nombre, String deporte,
                                   BigDecimal precioDia, BigDecimal precioNoche, String estado) {
        this(idEvento, ocurridoEn, idCancha, nombre, deporte, precioDia, precioNoche, estado, null, null, null, null, null);
    }
}
