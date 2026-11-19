package pe.alcanfores.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de cancha. En el portal ({@code GET /api/catalogo/canchas/{idOSlug}}) {@code estado}, {@code orden},
 * {@code creadoEn} y {@code actualizadoEn} también se envían (siempre ACTIVA); el panel los usa para editar.
 */
public record CanchaDetalleRespuesta(
        Long id,
        String slug,
        String nombre,
        String deporte,
        String deporteTexto,
        String superficie,
        Integer capacidad,
        boolean techada,
        boolean iluminacionLed,
        String descripcion,
        String descripcionLarga,
        String medidas,
        List<String> caracteristicas,
        BigDecimal precioDia,
        BigDecimal precioNoche,
        String fotoPrincipal,
        List<FotoRespuesta> fotos,
        CalificacionRespuesta calificacion,
        ResumenResenasRespuesta resumenResenas,
        String estado,
        Integer orden,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
}
