package pe.alcanfores.auth.dto;

import java.time.LocalDateTime;

/** Fila de la bitácora central. */
public record BitacoraRespuesta(Long id, String servicio, Long idActor, String actor, String rol, String accion,
                                String entidad, String idEntidad, String detalle, String ip, LocalDateTime fecha) {
}
