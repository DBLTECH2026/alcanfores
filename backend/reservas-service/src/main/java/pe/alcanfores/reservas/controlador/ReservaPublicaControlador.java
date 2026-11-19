package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.ComponentesSeguridad;
import pe.alcanfores.comun.seguridad.InternoFiltro;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReprogramacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.servicio.ReservaServicio;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas (portal)", description = "Registrar, consultar, cambiar de horario y cancelar una reserva con código + DNI")
public class ReservaPublicaControlador {

    /** Cabecera con la que Canchita (asistente con IA) identifica su conversación. */
    public static final String CABECERA_CONVERSACION = "X-Canchita-Conversacion";
    private static final Pattern CONVERSACION = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");

    private final ReservaServicio servicio;
    private final ComponentesSeguridad seguridad;

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
        validarCodigo(codigo);
        return servicio.cancelar(codigo, solicitud);
    }

    @PostMapping("/{codigo}/reprogramar")
    @Operation(summary = "Cambiar el horario de mi reserva", description = "Otros turnos (1 a 8, misma cancha y fecha; puede "
            + "cambiar de cancha y de día) conservando lo pagado. Hasta 2 horas antes del partido y una sola vez por reserva "
            + "(422 REPROGRAMACION_NO_PERMITIDA). 409 TURNO_OCUPADO si otro cliente tomó un turno; 422 REPROGRAMAR_MONTO_MENOR "
            + "si el nuevo horario cuesta menos de lo pagado. Responde lo mismo que «Consultar mi reserva». Canchita llama "
            + "con X-Interno-Token y X-Canchita-Conversacion (actor «Canchita (IA)»); un token inválido se ignora.")
    public ReservaClienteDetalle reprogramar(
            @PathVariable String codigo, @Valid @RequestBody ReprogramacionClienteSolicitud solicitud,
            @Parameter(hidden = true) @RequestHeader(value = InternoFiltro.CABECERA, required = false) String tokenInterno,
            @Parameter(hidden = true) @RequestHeader(value = CABECERA_CONVERSACION, required = false) String conversacion) {
        validarCodigo(codigo);
        return servicio.reprogramar(codigo, solicitud, conversacionCanchita(tokenInterno, conversacion));
    }

    /**
     * La conversación de Canchita solo cuenta con un {@code X-Interno-Token} válido (el mismo validador de
     * {@code /interno/**}) y un identificador corto y limpio; si no, la llamada es del cliente.
     */
    String conversacionCanchita(String tokenInterno, String conversacion) {
        boolean valida = conversacion != null && CONVERSACION.matcher(conversacion).matches();
        return valida && seguridad.internoFiltro().tokenValido(tokenInterno) ? conversacion : null;
    }

    /**
     * El código se valida aquí y no con @CodigoReserva: si un @PathVariable lleva restricciones, Spring valida todo el
     * método y los errores del cuerpo perderían el nombre de su campo.
     */
    private static void validarCodigo(String codigo) {
        if (!new CodigoReserva.Validador().isValid(codigo, null)) {
            throw NegocioExcepcion.validacion("codigo", Mensajes.CODIGO_RESERVA);
        }
    }

    @GetMapping("/{codigo}/constancia.pdf")
    @Operation(summary = "Descargar constancia PDF", description = "Constancia A4 con código, QR, cancha, fecha, hora, montos y condiciones.")
    public ResponseEntity<byte[]> constancia(@PathVariable @CodigoReserva(message = Mensajes.CODIGO_RESERVA) String codigo,
                                             @RequestParam @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return Archivos.pdf(servicio.constancia(codigo, dni), "constancia-" + codigo.trim().toUpperCase() + ".pdf");
    }
}
