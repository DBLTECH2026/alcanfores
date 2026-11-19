package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioCanchaRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioSolicitud;
import pe.alcanfores.catalogo.dto.HorarioDtos.TurnoVistaPrevia;

import java.util.List;

/** Horario propio de cada cancha (días, duración del turno, inicio de la noche y feriados). */
public interface HorarioServicio {

    HorarioCanchaRespuesta horario(Long idCancha);

    /** Valida y guarda el horario; publica {@code cancha.actualizada} (reservas regenera los turnos futuros). */
    HorarioCanchaRespuesta guardar(Long idCancha, HorarioSolicitud solicitud);

    /** Turnos que salen el día {@code dia} (1..7) con el horario guardado. */
    List<TurnoVistaPrevia> vistaPrevia(Long idCancha, int dia);

    /** Turnos que saldrían el día {@code dia} con el horario enviado (sin guardar). */
    List<TurnoVistaPrevia> vistaPrevia(Long idCancha, int dia, HorarioSolicitud solicitud);
}
