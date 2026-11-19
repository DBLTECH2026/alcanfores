package pe.alcanfores.catalogo.dto;

import pe.alcanfores.comun.eventos.HorarioDia;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Datos que necesitan reservas-service y pagos-service ({@code /interno/canchas}), con el horario propio de la cancha
 * (los 7 días; el cierre a medianoche llega 23:59:59) y qué hace en feriado.
 */
public record CanchaInternaRespuesta(
        Long id,
        String slug,
        String nombre,
        String deporte,
        String deporteTexto,
        Integer capacidad,
        BigDecimal precioDia,
        BigDecimal precioNoche,
        String estado,
        Integer orden,
        Integer duracionTurnoMin,
        LocalTime inicioNoche,
        List<HorarioDia> dias,
        String feriadoModo,
        BigDecimal recargoFeriado
) {
}
