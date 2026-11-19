package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.reservas.dto.AdminDtos.AsistenciaSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.CancelacionAdminSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ListaReservasRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ReprogramacionAdminSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaAdminDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualSolicitud;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.servicio.ReservaAdminServicio;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas (panel)", description = "Listado, detalle, reserva manual, cambio de horario, cancelación y asistencia (ADMIN y RECEPCION)")
public class ReservaAdminControlador {

    private final ReservaAdminServicio servicio;

    @GetMapping
    @Operation(summary = "Listar reservas", description = "Filtros: estado, estadoPago, fecha, desde, hasta, idCancha, origen y q "
            + "(código, DNI, nombre o celular). Orden: fecha, fechaRegistro, cancha, cliente, montoTotal, estado.")
    public ListaReservasRespuesta listar(@RequestParam(required = false) EstadoReserva estado,
                                         @RequestParam(required = false) EstadoPago estadoPago,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Long idCancha,
                                         @RequestParam(required = false) Origen origen,
                                         @RequestParam(required = false) String q,
                                         @PageableDefault(size = 20) Pageable pagina) {
        return servicio.listar(new ReservaAdminServicio.Filtro(estado, estadoPago, fecha, desde, hasta, idCancha, origen, q), pagina);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Detalle de la reserva", description = "Datos completos, línea de tiempo y cancelación.")
    public ReservaAdminDetalle detalle(@PathVariable String codigo) {
        return servicio.detalle(codigo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserva manual", description = "Presencial o telefónica, sin plazo. Con pagoInicial registra el cobro en "
            + "pagos-service; si pagos no responde, la reserva queda PENDIENTE y se devuelve un aviso.")
    public ReservaManualRespuesta registrar(@Valid @RequestBody ReservaManualSolicitud solicitud) {
        return servicio.registrarManual(solicitud);
    }

    @PostMapping("/{codigo}/cancelar")
    @Operation(summary = "Cancelar reserva", description = "devolucion vacío aplica la regla de horas; true/false lo decide el panel.")
    public ReservaAdminDetalle cancelar(@PathVariable String codigo, @Valid @RequestBody CancelacionAdminSolicitud solicitud) {
        return servicio.cancelar(codigo, solicitud);
    }

    @PostMapping("/{codigo}/asistencia")
    @Operation(summary = "Registrar asistencia", description = "Solo reservas confirmadas y desde 15 minutos antes del inicio.")
    public ReservaAdminDetalle asistencia(@PathVariable String codigo, @Valid @RequestBody AsistenciaSolicitud solicitud) {
        return servicio.registrarAsistencia(codigo, solicitud);
    }

    @PostMapping("/{codigo}/reprogramar")
    @Operation(summary = "Cambiar el horario (reprogramar)", description = "ADMIN y RECEPCION. Otros turnos (1 a 8, misma cancha "
            + "y fecha; puede cambiar de cancha y de día) conservando lo pagado y con motivo (5 a 200). Sin el plazo ni el "
            + "límite de un cambio del cliente. 409 TURNO_OCUPADO, 422 RESERVA_NO_REPROGRAMABLE (cancelada o con asistencia), "
            + "422 REPROGRAMAR_MONTO_MENOR. Acepta el código (ALC-…) o el id numérico. Responde el detalle con «reprogramaciones».")
    public ReservaAdminDetalle reprogramar(@PathVariable String codigo, @Valid @RequestBody ReprogramacionAdminSolicitud solicitud) {
        return servicio.reprogramar(codigo, solicitud);
    }

    @GetMapping("/{codigo}/constancia.pdf")
    @Operation(summary = "Constancia PDF", description = "La misma constancia que descarga el cliente.")
    public ResponseEntity<byte[]> constancia(@PathVariable String codigo) {
        return Archivos.pdf(servicio.constancia(codigo), "constancia-" + codigo.trim().toUpperCase() + ".pdf");
    }
}
