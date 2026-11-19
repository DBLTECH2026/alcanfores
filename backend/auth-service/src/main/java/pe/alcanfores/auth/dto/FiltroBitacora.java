package pe.alcanfores.auth.dto;

import java.time.LocalDate;

/** Filtros de la bitácora: todos opcionales; {@code desde/hasta} son fechas inclusivas. */
public record FiltroBitacora(String servicio, String actor, String accion, String entidad, LocalDate desde,
                             LocalDate hasta) {
}
