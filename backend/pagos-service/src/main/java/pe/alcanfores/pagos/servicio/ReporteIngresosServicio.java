package pe.alcanfores.pagos.servicio;

import pe.alcanfores.pagos.dto.ReporteIngresosRespuesta;

import java.time.LocalDate;

/** Ingresos validados por periodo (por método, canal, día y tipo de comprobante). */
public interface ReporteIngresosServicio {

    /** Sin fechas: últimos 30 días. Máximo 366 días. */
    ReporteIngresosRespuesta generar(LocalDate desde, LocalDate hasta);
}
