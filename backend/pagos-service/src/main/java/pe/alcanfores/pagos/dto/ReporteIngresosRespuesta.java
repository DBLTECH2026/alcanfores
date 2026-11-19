package pe.alcanfores.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/admin/reportes/ingresos}: ingresos validados del periodo. */
public record ReporteIngresosRespuesta(LocalDate desde, LocalDate hasta, BigDecimal total, long cantidadPagos,
                                       BigDecimal ticketPromedio, List<Grupo> porMetodo, List<Grupo> porCanal,
                                       List<Grupo> porTipoPago, List<Dia> porDia, List<Grupo> porTipoComprobante,
                                       Double tiempoMedioValidacionMin, long pagosRechazados, BigDecimal reembolsado) {

    /** {@code clave} es el valor (YAPE, PASARELA, BOLETA…) y {@code texto} su nombre legible. */
    public record Grupo(String clave, String texto, BigDecimal total, long cantidad) {
    }

    public record Dia(LocalDate fecha, BigDecimal total, long cantidad) {
    }
}
