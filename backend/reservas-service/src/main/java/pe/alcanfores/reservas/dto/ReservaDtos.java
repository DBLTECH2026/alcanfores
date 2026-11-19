package pe.alcanfores.reservas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.comun.validacion.NombrePersona;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Solicitudes y respuestas del portal público de reservas. */
public final class ReservaDtos {

    public static final String PATRON_CORREO = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$";

    private ReservaDtos() {
    }

    /** Datos del cliente (se normalizan: nombres en Título, celular de 9 dígitos, correo en minúsculas). */
    public record ClienteSolicitud(
            @NotNull(message = Mensajes.DNI) @Dni String dni,
            @NotNull(message = Mensajes.NOMBRE) @NombrePersona String nombres,
            @NotNull(message = Mensajes.NOMBRE) @NombrePersona String apellidos,
            @NotNull(message = Mensajes.CELULAR) @Celular String telefono,
            @NotBlank(message = Mensajes.CORREO) @Email(message = Mensajes.CORREO, regexp = PATRON_CORREO)
            @Size(max = 100, message = Mensajes.CORREO) String correo) {
    }

    public record RegistroReservaSolicitud(
            @NotNull(message = Mensajes.ELEGIR_TURNO) @Positive(message = Mensajes.ELEGIR_TURNO) Long idTurno,
            @NotNull(message = Mensajes.DATOS_CLIENTE) @Valid ClienteSolicitud cliente,
            @NotNull(message = Mensajes.METODO_PAGO) @Pattern(regexp = "YAPE|PLIN|TARJETA|MERCADO_PAGO",
                    message = Mensajes.METODO_PAGO) String metodoPago,
            @NotNull(message = Mensajes.TERMINOS) @AssertTrue(message = Mensajes.TERMINOS) Boolean aceptaTerminos,
            @NotNull(message = Mensajes.TERMINOS) @AssertTrue(message = Mensajes.TERMINOS) Boolean aceptaPrivacidad) {
    }

    public record CancelacionClienteSolicitud(
            @NotNull(message = Mensajes.DNI) @Dni String dni,
            @TextoEntre(min = 10, max = 255, message = Mensajes.MOTIVO) String motivo) {
    }

    public record CanchaResumen(Long id, String nombre, String deporte) {
    }

    /** Cliente en vistas públicas: DNI enmascarado (Ley 29733). */
    public record ClientePublico(String nombres, String apellidos, String dniEnmascarado, String telefono, String correo) {
    }

    public record ReservaCreadaRespuesta(String codigoReserva, EstadoReserva estado, EstadoPago estadoPago,
                                         LocalDateTime venceEn, CanchaResumen cancha, LocalDate fecha, LocalTime horaInicio,
                                         LocalTime horaFin, BigDecimal montoTotal, BigDecimal montoAdelanto,
                                         ClientePublico cliente) {
    }

    public record CancelacionInfo(String canceladoPor, String motivo, LocalDateTime fecha, boolean devolucion,
                                  BigDecimal montoDevolucion) {
    }

    /** Hito de la línea de tiempo. evento: REGISTRADA, COMPROBANTE_RECIBIDO, OBSERVADO, PAGO_VALIDADO, CONFIRMADA, ASISTENCIA, CANCELADA. */
    public record EventoLinea(String evento, LocalDateTime fecha, String detalle) {
    }

    public record ReservaClienteDetalle(String codigoReserva, EstadoReserva estado, EstadoPago estadoPago,
                                        LocalDateTime venceEn, CanchaResumen cancha, LocalDate fecha, LocalTime horaInicio,
                                        LocalTime horaFin, BigDecimal montoTotal, BigDecimal montoAdelanto,
                                        ClientePublico cliente, Asistencia asistencia, BigDecimal montoPagado,
                                        BigDecimal saldo, String metodoPagoPrevisto, CancelacionInfo cancelacion,
                                        List<EventoLinea> linea, boolean puedeCancelar, boolean puedeReenviarComprobante,
                                        boolean puedeResenar) {
    }
}
