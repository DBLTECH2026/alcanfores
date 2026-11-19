package pe.alcanfores.reservas.servicio;

import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaTurno;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.GeneracionRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.GenerarTurnosSolicitud;

import java.time.LocalDate;
import java.util.Collection;

/** Generación, agenda y bloqueo de turnos. */
public interface TurnoServicio {

    /** Genera (sin duplicar) los turnos de hoy + días visibles para todas las canchas. */
    GeneracionRespuesta generarVentana();

    /** Genera (sin duplicar) los turnos de un rango; {@code idsCancha} vacío = todas. Sin validaciones de rango (uso interno). */
    GeneracionRespuesta generarRango(LocalDate desde, LocalDate hasta, Collection<Long> idsCancha);

    /** {@code POST /api/admin/turnos/generar} (valida el rango). */
    GeneracionRespuesta generar(GenerarTurnosSolicitud solicitud);

    AgendaRespuesta agenda(LocalDate fecha, Long idCancha);

    AgendaTurno bloquear(Long idTurno, String motivo);

    AgendaTurno desbloquear(Long idTurno, String motivo);

    BloqueoRangoRespuesta bloquearRango(BloqueoRangoSolicitud solicitud);

    /** Repone nombre y precio de los turnos futuros y bloquea/desbloquea si la cancha cambió de estado. */
    void aplicarCambioCancha(CanchaCatalogo cancha);

    /** Refresca catálogo y aplica los cambios de cada cancha a sus turnos futuros. */
    void sincronizarCatalogo();

    /** Consumidor idempotente de {@code cancha.actualizada}. */
    void alActualizarCancha(CanchaActualizadaEvento evento);
}
