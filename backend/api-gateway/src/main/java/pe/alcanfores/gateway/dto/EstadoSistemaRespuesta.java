package pe.alcanfores.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;

/** {@code GET /api/estado}: {@code {generadoEn, servicios:[{nombre, estado, instancias, latenciaMs}]}} */
public record EstadoSistemaRespuesta(LocalDateTime generadoEn, List<EstadoServicio> servicios) {
}
