package pe.alcanfores.comun.eventos;

import java.time.LocalDateTime;
import java.util.UUID;

/** auditoria.accion · toda acción administrativa que cambia datos (RNF-19). La guarda auth-service en la bitácora. */
public record AccionAuditadaEvento(UUID idEvento, LocalDateTime ocurridoEn, String servicio, Long idActor, String actor, String rol, String accion, String entidad, String idEntidad, String detalle, String ip) implements EventoDominio {
}
