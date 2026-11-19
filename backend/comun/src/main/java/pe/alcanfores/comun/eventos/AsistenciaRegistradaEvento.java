package pe.alcanfores.comun.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** reserva.asistencia · asistencia = ASISTIO o NO_ASISTIO. */
public record AsistenciaRegistradaEvento(UUID idEvento, LocalDateTime ocurridoEn, DatosReserva reserva, String asistencia) implements EventoDominio {
}
