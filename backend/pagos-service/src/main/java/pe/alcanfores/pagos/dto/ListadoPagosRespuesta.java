package pe.alcanfores.pagos.dto;

import java.util.List;
import java.util.Map;

/** Listado paginado de pagos + conteo por estado (para las pestañas del panel). */
public record ListadoPagosRespuesta(List<PagoResumenRespuesta> contenido, int pagina, int tamanio, long totalElementos,
                                    int totalPaginas, Map<String, Long> conteos) {
}
