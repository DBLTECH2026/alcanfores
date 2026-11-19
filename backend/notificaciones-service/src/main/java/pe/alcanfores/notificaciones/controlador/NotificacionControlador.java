package pe.alcanfores.notificaciones.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.dto.NotificacionResumen;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.servicio.FiltroNotificaciones;
import pe.alcanfores.notificaciones.servicio.NotificacionServicio;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Notificaciones", description = "Bandeja de avisos enviados al cliente por correo y WhatsApp")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/notificaciones")
@Validated
public class NotificacionControlador {

    private static final Set<String> ORDEN_PERMITIDO = Set.of("creadoEn", "enviadoEn", "estado", "canal", "evento", "intentos");

    private final NotificacionServicio servicio;

    public NotificacionControlador(NotificacionServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Lista la bandeja de notificaciones",
            description = "Filtros opcionales por canal, estado, evento, texto (código, destino o asunto) y fechas de creación.")
    @GetMapping
    public PaginaRespuesta<NotificacionResumen> listar(
            @Parameter(description = "WHATSAPP o CORREO") @RequestParam(required = false) CanalNotificacion canal,
            @Parameter(description = "PENDIENTE, ENVIADO, FALLIDO o SIMULADO") @RequestParam(required = false) EstadoNotificacion estado,
            @Parameter(description = "Evento de origen, p. ej. reserva.confirmada o recordatorio")
            @RequestParam(required = false) @Size(max = 40, message = "El evento tiene como máximo 40 caracteres.") String evento,
            @Parameter(description = "Código de reserva, correo, celular o asunto")
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda tiene como máximo 60 caracteres.") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño de página va de 1 a 100.")
            @Max(value = 100, message = "El tamaño de página va de 1 a 100.") int size,
            @Parameter(description = "campo,desc · campos: creadoEn, enviadoEn, estado, canal, evento, intentos")
            @RequestParam(required = false) String sort) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        return servicio.listar(new FiltroNotificaciones(canal, estado, evento, q, desde, hasta),
                Paginacion.de(page, size, sort, ORDEN_PERMITIDO, Sort.by(Sort.Direction.DESC, "creadoEn", "id")));
    }

    @Operation(summary = "Detalle de una notificación",
            description = "Incluye el cuerpo tal como salió (HTML o texto), una vista previa del correo con el logo incrustado y los adjuntos.")
    @GetMapping("/{id}")
    public NotificacionDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @Operation(summary = "Reintenta un aviso fallido (solo ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reintentar")
    public NotificacionDetalle reintentar(@PathVariable Long id) {
        return servicio.reintentar(id);
    }
}
