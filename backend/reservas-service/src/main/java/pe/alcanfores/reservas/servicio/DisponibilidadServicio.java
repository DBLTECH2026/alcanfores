package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.DisponibilidadDtos.DiaCalendario;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.DisponibilidadRespuesta;

import java.time.LocalDate;
import java.util.List;

/** Consulta pública de turnos libres. */
public interface DisponibilidadServicio {

    DisponibilidadRespuesta disponibilidad(LocalDate fecha, Long idCancha);

    List<DiaCalendario> calendario(LocalDate desde, Integer dias, Long idCancha);
}
