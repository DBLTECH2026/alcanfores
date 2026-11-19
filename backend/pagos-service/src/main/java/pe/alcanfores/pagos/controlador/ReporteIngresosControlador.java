package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.pagos.dto.ReporteIngresosRespuesta;
import pe.alcanfores.pagos.servicio.ReporteIngresosServicio;

import java.time.LocalDate;

@Tag(name = "Reportes de ingresos", description = "Solo ADMIN")
@SecurityRequirement(name = "bearer")
@RestController
public class ReporteIngresosControlador {

    private final ReporteIngresosServicio reportes;

    public ReporteIngresosControlador(ReporteIngresosServicio reportes) {
        this.reportes = reportes;
    }

    @Operation(summary = "Ingresos del periodo", description = "Total, por método, canal, tipo de pago, día y tipo de comprobante; tiempo medio de validación y rechazos. Sin fechas: últimos 30 días.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/reportes/ingresos")
    public ReporteIngresosRespuesta ingresos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.generar(desde, hasta);
    }
}
