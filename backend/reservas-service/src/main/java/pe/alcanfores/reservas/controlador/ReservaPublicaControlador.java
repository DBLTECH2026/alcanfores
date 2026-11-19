package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.servicio.ReservaServicio;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas (portal)", description = "Registrar, consultar y cancelar una reserva con código + DNI")
public class ReservaPublicaControlador {

    private final ReservaServicio servicio;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar reserva", description = "Crea la reserva PENDIENTE con plazo para pagar el adelanto. "
            + "409 TURNO_OCUPADO si otra persona tomó el turno.")
    public ReservaCreadaRespuesta registrar(@Valid @RequestBody RegistroReservaSolicitud solicitud) {
        return servicio.registrar(solicitud);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Consultar mi reserva", description = "Estado, montos, línea de tiempo y acciones disponibles.")
    public ReservaClienteDetalle consultar(@PathVariable @CodigoReserva(message = Mensajes.CODIGO_RESERVA) String codigo,
                                          @RequestParam @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return servicio.consultar(codigo, dni);
    }

    @PostMapping("/{codigo}/cancelar")
    @Operation(summary = "Cancelar mi reserva", description = "Solo antes del inicio; hay devolución si faltan las horas configuradas y hubo pago.")
    public ReservaClienteDetalle cancelar(@PathVariable String codigo, @Valid @RequestBody CancelacionClienteSolicitud solicitud) {
        // El código se valida aquí y no con @CodigoReserva: si un @PathVariable lleva restricciones, Spring valida todo el
        // método y los errores del cuerpo perderían el nombre de su campo.
        if (!new CodigoReserva.Validador().isValid(codigo, null)) {
            throw NegocioExcepcion.validacion("codigo", Mensajes.CODIGO_RESERVA);
        }
        return servicio.cancelar(codigo, solicitud);
    }

    @GetMapping("/{codigo}/constancia.pdf")
    @Operation(summary = "Descargar constancia PDF", description = "Constancia A4 con código, QR, cancha, fecha, hora, montos y condiciones.")
    public ResponseEntity<byte[]> constancia(@PathVariable @CodigoReserva(message = Mensajes.CODIGO_RESERVA) String codigo,
                                             @RequestParam @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return Archivos.pdf(servicio.constancia(codigo, dni), "constancia-" + codigo.trim().toUpperCase() + ".pdf");
    }
}
