package pe.alcanfores.notificaciones.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.notificaciones.dto.EjecucionRecordatoriosRespuesta;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.util.Set;

@Tag(name = "Recordatorios", description = "Recordatorio automático antes del partido (cron cada minuto)")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/recordatorios")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class RecordatorioControlador {

    private static final Set<String> ORDEN_PERMITIDO = Set.of("enviarEn", "inicioReserva", "estado", "enviadoEn", "minutosAntes");

    private final RecordatorioServicio servicio;

    public RecordatorioControlador(RecordatorioServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Lista los recordatorios",
            description = "Filtros opcionales: estado (PROGRAMADO, ENVIADO, CANCELADO u OMITIDO) y codigo de reserva.")
    @GetMapping
    public PaginaRespuesta<RecordatorioRespuesta> listar(
            @RequestParam(required = false) EstadoRecordatorio estado,
            @RequestParam(required = false) @Size(max = 12, message = "El código de reserva tiene el formato ALC-XXXXXX.")
            String codigo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño de página va de 1 a 100.")
            @Max(value = 100, message = "El tamaño de página va de 1 a 100.") int size,
            @RequestParam(required = false) String sort) {
        return servicio.listar(estado, codigo, Paginacion.de(page, size, sort, ORDEN_PERMITIDO,
                Sort.by(Sort.Direction.DESC, "enviarEn", "id")));
    }

    @Operation(summary = "Ejecuta ahora el job de recordatorios",
            description = "Envía los recordatorios cuya hora ya llegó (útil para la demo sin esperar al cron).")
    @PostMapping("/ejecutar")
    public EjecucionRecordatoriosRespuesta ejecutar() {
        return servicio.ejecutarAhora();
    }
}
