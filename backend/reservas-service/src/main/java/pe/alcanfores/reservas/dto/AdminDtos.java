package pe.alcanfores.reservas.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.Ruc;
import pe.alcanfores.comun.validacion.SinEnlaces;
import pe.alcanfores.reservas.dto.ReservaDtos.CanchaResumen;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionInfo;
import pe.alcanfores.reservas.dto.ReservaDtos.ClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.EventoLinea;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.dto.ReservaDtos.TurnoReserva;
import pe.alcanfores.reservas.dto.ReservaDtos.TurnosPedidos;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Franja;
import pe.alcanfores.reservas.modelo.Origen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Solicitudes y respuestas del panel de administración de reservas. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record ComprobanteSolicitud(
            @NotNull(message = Mensajes.TIPO_COMPROBANTE) @Pattern(regexp = "BOLETA|FACTURA",
                    message = Mensajes.TIPO_COMPROBANTE) String tipo,
            @Ruc String ruc,
            @Size(max = 150, message = Mensajes.FACTURA) String razonSocial,
            @Size(max = 200, message = Mensajes.FACTURA) String direccionFiscal) {
    }

    public record PagoInicialSolicitud(
            @NotNull(message = Mensajes.TIPO_PAGO) @Pattern(regexp = "ADELANTO|TOTAL", message = Mensajes.TIPO_PAGO) String tipoPago,
            @NotNull(message = Mensajes.METODO_PAGO) @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA_POS",
                    message = Mensajes.METODO_PAGO) String metodo,
            @NotNull(message = Mensajes.MONTO) @DecimalMin(value = "0.01", message = Mensajes.MONTO)
            @Digits(integer = 5, fraction = 2, message = Mensajes.MONTO) BigDecimal monto,
            @Pattern(regexp = "^\\d{6,12}$", message = Mensajes.CODIGO_OPERACION) String codigoOperacion,
            @NotNull(message = Mensajes.TIPO_COMPROBANTE) @Valid ComprobanteSolicitud comprobante) {
    }

    public record ReservaManualSolicitud(
            @Positive(message = Mensajes.ELEGIR_TURNO) Long idTurno,
            @Size(max = Mensajes.MAX_TURNOS, message = Mensajes.TURNOS_CANTIDAD)
            List<@NotNull(message = Mensajes.ELEGIR_TURNO) @Positive(message = Mensajes.ELEGIR_TURNO) Long> idsTurno,
            @NotNull(message = Mensajes.DATOS_CLIENTE) @Valid ClienteSolicitud cliente,
            @NotNull(message = Mensajes.ORIGEN) @Pattern(regexp = "PRESENCIAL|TELEFONICA", message = Mensajes.ORIGEN) String origen,
            @Size(max = 255, message = Mensajes.NOTA) String nota,
            @Valid PagoInicialSolicitud pagoInicial) {

        public TurnosPedidos turnos() {
            return TurnosPedidos.de(idTurno, idsTurno);
        }
    }

    /** Respuesta de la reserva manual: la reserva creada + si el cobro inicial quedó registrado en pagos-service. */
    public record ReservaManualRespuesta(@JsonUnwrapped ReservaCreadaRespuesta reserva, boolean pagoRegistrado, String aviso) {
    }

    /** devolucion: si es null se aplica la regla (faltan ≥ devolucionHoras y hubo pago). */
    public record CancelacionAdminSolicitud(
            @TextoEntre(min = 10, max = 255, message = Mensajes.MOTIVO) String motivo,
            Boolean devolucion) {
    }

    /** Cambiar el horario desde el panel: turnos nuevos (1 a 8, misma cancha y fecha) y el motivo (queda en el historial). */
    public record ReprogramacionAdminSolicitud(
            @NotNull(message = Mensajes.TURNOS_CANTIDAD) @Size(min = 1, max = Mensajes.MAX_TURNOS, message = Mensajes.TURNOS_CANTIDAD)
            List<@NotNull(message = Mensajes.ELEGIR_TURNO) @Positive(message = Mensajes.ELEGIR_TURNO) Long> idsTurno,
            @TextoEntre(min = 5, max = 200, message = Mensajes.MOTIVO_REPROGRAMAR)
            @SinEnlaces(message = Mensajes.MOTIVO_REPROGRAMAR) String motivo) {
    }

    /** Un horario de la reserva en el historial de cambios (antes o después). */
    public record HorarioReprogramado(Long idCancha, String cancha, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                      String horarioTexto, List<Long> idsTurno, int cantidadTurnos, BigDecimal total) {
    }

    /** Cambio de horario: actor CLIENTE, CANCHITA o PANEL; usuario = quién (usuario del panel, «Canchita (IA)» o el cliente). */
    public record ReprogramacionInfo(Long id, String actor, String usuario, String motivo, LocalDateTime creadoEn,
                                     HorarioReprogramado anterior, HorarioReprogramado nuevo) {
    }

    public record AsistenciaSolicitud(
            @NotNull(message = Mensajes.ASISTENCIA) @Pattern(regexp = "ASISTIO|NO_ASISTIO", message = Mensajes.ASISTENCIA)
            String asistencia) {
    }

    public record ClienteFila(String nombres, String apellidos, String dni, String telefono) {
    }

    public record ReservaFila(String codigoReserva, ClienteFila cliente, String cancha, LocalDate fecha, LocalTime horaInicio,
                              LocalTime horaFin, EstadoReserva estado, EstadoPago estadoPago, Asistencia asistencia,
                              Origen origen, BigDecimal montoTotal, BigDecimal montoPagado, LocalDateTime fechaRegistro,
                              List<TurnoReserva> turnos, String horarioTexto, int cantidadTurnos,
                              boolean puedeMarcarAsistencia) {
    }

    /** Página de reservas + conteos por estado (PENDIENTE, CONFIRMADA, CANCELADA y EN_REVISION) para las pestañas. */
    public record ListaReservasRespuesta(List<ReservaFila> contenido, int pagina, int tamanio, long totalElementos,
                                         int totalPaginas, Map<String, Long> conteos) {
    }

    public record ClienteCompleto(Long idCliente, String dni, String nombres, String apellidos, String telefono,
                                  String correo, boolean verificadoReniec) {
    }

    public record ReservaAdminDetalle(String codigoReserva, EstadoReserva estado, EstadoPago estadoPago,
                                      Asistencia asistencia, Origen origen, String metodoPagoPrevisto, CanchaResumen cancha,
                                      Long idTurno, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Franja franja,
                                      BigDecimal montoTotal, BigDecimal montoAdelanto, BigDecimal montoPagado,
                                      BigDecimal saldo, LocalDateTime venceEn, LocalDateTime fechaRegistro,
                                      LocalDateTime confirmadaEn, Long registradoPor, String nota, ClienteCompleto cliente,
                                      CancelacionInfo cancelacion, List<EventoLinea> linea, boolean puedeCancelar,
                                      boolean puedeMarcarAsistencia, boolean puedeCobrarSaldo, List<TurnoReserva> turnos,
                                      String horarioTexto, int cantidadTurnos, boolean puedeReprogramar,
                                      List<ReprogramacionInfo> reprogramaciones) {
    }

    public record ClienteResumen(Long idCliente, String dni, String nombres, String apellidos, String telefono,
                                 String correo, boolean verificadoReniec, LocalDateTime fechaRegistro, long totalReservas,
                                 long asistidas, long noShows, LocalDate ultimaReserva) {
    }

    public record ClienteDetalle(ClienteResumen cliente, List<ReservaFila> reservas) {
    }
}
