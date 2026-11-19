package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.PanelDtos.ReporteReservas;

import java.time.LocalDate;

/** Reportes de reservas (ADMIN): totales, ocupación, por cancha, día, hora y origen, cancelaciones y top clientes. */
public interface ReporteServicio {

    ReporteReservas reporte(LocalDate desde, LocalDate hasta);

    /** Excel con hojas Resumen, Por cancha, Por día y Reservas. */
    byte[] excel(LocalDate desde, LocalDate hasta);
}
