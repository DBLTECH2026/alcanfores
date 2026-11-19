package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteResumen;
import pe.alcanfores.reservas.dto.PanelDtos.DashboardRespuesta;
import pe.alcanfores.reservas.dto.PanelDtos.ReporteReservas;
import pe.alcanfores.reservas.servicio.PanelServicio;
import pe.alcanfores.reservas.servicio.ReporteServicio;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Panel", description = "Dashboard del día, clientes con métricas y reportes de reservas")
public class PanelControlador {

    private final PanelServicio panel;
    private final ReporteServicio reportes;

    @GetMapping("/api/admin/dashboard")
    @Operation(summary = "Dashboard del día", description = "KPI, próximas 5 reservas, agenda por cancha y alertas.")
    public DashboardRespuesta dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return panel.dashboard(fecha);
    }

    @GetMapping("/api/admin/clientes")
    @Operation(summary = "Clientes", description = "Con totalReservas, asistidas, noShows y ultimaReserva. q busca por DNI, nombre, celular o correo.")
    public PaginaRespuesta<ClienteResumen> clientes(@RequestParam(required = false) String q,
                                                    @PageableDefault(size = 20) Pageable pagina) {
        return panel.clientes(q, pagina);
    }

    @GetMapping("/api/admin/clientes/{dni}")
    @Operation(summary = "Historial del cliente", description = "Métricas y todas sus reservas.")
    public ClienteDetalle cliente(@PathVariable String dni) {
        return panel.cliente(dni);
    }

    @GetMapping("/api/admin/reportes/reservas")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte de reservas (ADMIN)", description = "Por defecto los últimos 30 días.")
    public ReporteReservas reporte(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.reporte(desde, hasta);
    }

    @GetMapping("/api/admin/reportes/reservas.xlsx")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte de reservas en Excel (ADMIN)", description = "Hojas Resumen, Por cancha, Por día y Reservas.")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Archivos.excel(reportes.excel(desde, hasta), "reporte-reservas.xlsx");
    }
}
