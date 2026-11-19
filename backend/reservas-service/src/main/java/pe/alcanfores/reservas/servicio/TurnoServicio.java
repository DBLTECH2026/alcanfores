package pe.alcanfores.reservas.servicio;

import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaTurno;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.GeneracionRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.GenerarTurnosSolicitud;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Turnos generados según el horario de CADA cancha y el calendario (feriados y días no laborables), agenda y bloqueos.
 * Regenerar es idempotente: crea los que faltan, borra los libres que ya no calzan, reprecia y aplica o quita los bloqueos
 * automáticos; los turnos con reserva vigente y los bloqueos manuales se conservan.
 */
public interface TurnoServicio {

    /** Regenera hoy + días generados para todas las canchas (arranque y tarea de las 00:05). */
    GeneracionRespuesta generarVentana();

    /** Regenera un rango; {@code idsCancha} vacío = todas. Sin validaciones de rango (uso interno). */
    GeneracionRespuesta generarRango(LocalDate desde, LocalDate hasta, Collection<Long> idsCancha);

    /** {@code POST /api/admin/turnos/generar} (soporte; valida el rango). */
    GeneracionRespuesta generar(GenerarTurnosSolicitud solicitud);

    AgendaRespuesta agenda(LocalDate fecha, Long idCancha);

    AgendaTurno bloquear(Long idTurno, String motivo);

    AgendaTurno desbloquear(Long idTurno, String motivo);

    BloqueoRangoRespuesta bloquearRango(BloqueoRangoSolicitud solicitud);

    DesbloqueoRangoRespuesta desbloquearRango(DesbloqueoRangoSolicitud solicitud);

    /** Regenera los turnos futuros de la cancha con sus datos y horario nuevos. */
    void aplicarCambioCancha(CanchaCatalogo cancha);

    /** Refresca catálogo (canchas y calendario) y regenera lo que cambió. */
    void sincronizarCatalogo();

    /**
     * Si catálogo aún no se pudo leer (por ejemplo, arrancó después que reservas), lo intenta otra vez. Mientras no se lea,
     * la regeneración solo crea turnos; en cuanto se lee, se aplican horarios y calendario reales.
     */
    void sincronizarSiFalta();

    /** Consumidor idempotente de {@code cancha.actualizada}. */
    void alActualizarCancha(CanchaActualizadaEvento evento);

    /** Consumidor idempotente de {@code calendario.actualizado}: reevalúa los turnos de esa fecha. */
    void alActualizarCalendario(CalendarioActualizadoEvento evento);
}
