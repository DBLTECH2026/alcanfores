package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaTurno;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.GeneracionRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.GenerarTurnosSolicitud;
import pe.alcanfores.reservas.servicio.TurnoServicio;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/turnos")
@RequiredArgsConstructor
@Tag(name = "Agenda y turnos", description = "Agenda canchas × horas, bloqueos y generación de turnos")
public class TurnoAdminControlador {

    private final TurnoServicio servicio;

    @GetMapping
    @Operation(summary = "Agenda del día", description = "Canchas × turnos con la reserva vigente de cada turno.")
    public AgendaRespuesta agenda(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam(required = false) Long idCancha) {
        return servicio.agenda(fecha, idCancha);
    }

    @PostMapping("/generar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generar turnos (ADMIN, soporte)", description = "Los turnos se generan solos según el horario de "
            + "cada cancha; esto solo fuerza la regeneración de un rango. Idempotente.")
    public GeneracionRespuesta generar(@Valid @RequestBody GenerarTurnosSolicitud solicitud) {
        return servicio.generar(solicitud);
    }

    @PostMapping("/{id}/bloquear")
    @Operation(summary = "Bloquear turno", description = "No se puede si tiene una reserva vigente.")
    public AgendaTurno bloquear(@PathVariable Long id, @Valid @RequestBody BloqueoSolicitud solicitud) {
        return servicio.bloquear(id, solicitud.motivo());
    }

    @PostMapping("/{id}/desbloquear")
    @Operation(summary = "Desbloquear turno", description = "Vuelve a DISPONIBLE con el precio vigente de la cancha.")
    public AgendaTurno desbloquear(@PathVariable Long id, @Valid @RequestBody(required = false) DesbloqueoSolicitud solicitud) {
        return servicio.desbloquear(id, solicitud == null ? null : solicitud.motivo());
    }

    @PostMapping("/bloquear-rango")
    @Operation(summary = "Bloquear un rango", description = "Varias canchas, una fecha o varios días (hasta 31) y las horas "
            + "desde–hasta: bloquea los turnos libres que se crucen; los que tienen reserva se omiten y se devuelven sus códigos.")
    public BloqueoRangoRespuesta bloquearRango(@Valid @RequestBody BloqueoRangoSolicitud solicitud) {
        return servicio.bloquearRango(solicitud);
    }

    @PostMapping("/desbloquear-rango")
    @Operation(summary = "Desbloquear un rango", description = "Quita los bloqueos MANUALES del rango (los de feriado o día "
            + "no laborable se quitan desde el calendario).")
    public DesbloqueoRangoRespuesta desbloquearRango(@Valid @RequestBody DesbloqueoRangoSolicitud solicitud) {
        return servicio.desbloquearRango(solicitud);
    }
}
