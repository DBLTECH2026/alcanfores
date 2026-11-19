package pe.alcanfores.catalogo.dto;

public record FotoRespuesta(Long id, String url, String leyenda, boolean principal, Integer orden, Integer ancho, Integer alto) {
}
