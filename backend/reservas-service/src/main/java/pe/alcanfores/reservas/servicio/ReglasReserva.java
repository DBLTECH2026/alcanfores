package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;

import java.time.LocalDateTime;
import java.util.Map;

/** Reglas de negocio de la reserva que se muestran como banderas al cliente y al panel. */
public final class ReglasReserva {

    /** Minutos de anticipación mínima para reservar por la web. */
    public static final int MINUTOS_ANTICIPACION = 30;
    /** Desde cuántos minutos antes del inicio se puede registrar la asistencia. */
    public static final int MINUTOS_ASISTENCIA = 15;
    public static final String MOTIVO_VENCIDA = "Venció el plazo para pagar";

    private static final Map<String, String> DEPORTES = Map.of(
            "FUTBOL_5", "Fútbol 5", "FUTBOL_6", "Fútbol 6", "FUTBOL_7", "Fútbol 7",
            "VOLEY", "Vóley", "BASQUET", "Básquet", "MULTIUSO", "Multiusos (vóley y básquet)");

    private ReglasReserva() {
    }

    /** El cliente puede cancelar una reserva vigente mientras no haya empezado. */
    public static boolean puedeCancelarCliente(Reserva r, LocalDateTime ahora) {
        return r.vigente() && ahora.isBefore(r.inicio());
    }

    /** Puede (re)enviar su comprobante si la reserva sigue pendiente, sin pago u observada y dentro del plazo. */
    public static boolean puedeReenviarComprobante(Reserva r, LocalDateTime ahora) {
        return r.getEstado() == EstadoReserva.PENDIENTE
                && (r.getEstadoPago() == EstadoPago.SIN_PAGO || r.getEstadoPago() == EstadoPago.OBSERVADO)
                && (r.getVenceEn() == null || ahora.isBefore(r.getVenceEn()))
                && ahora.isBefore(r.inicio());
    }

    /** Puede calificar la cancha si la reserva está confirmada y ya empezó (o se marcó que asistió). */
    public static boolean puedeResenar(Reserva r, LocalDateTime ahora) {
        return r.getEstado() == EstadoReserva.CONFIRMADA && r.getAsistencia() != Asistencia.NO_ASISTIO
                && (r.getAsistencia() == Asistencia.ASISTIO || !ahora.isBefore(r.inicio()));
    }

    public static boolean puedeMarcarAsistencia(Reserva r, LocalDateTime ahora) {
        return r.getEstado() == EstadoReserva.CONFIRMADA && !ahora.isBefore(r.inicio().minusMinutes(MINUTOS_ASISTENCIA));
    }

    /** El panel puede cancelar una reserva vigente mientras no se haya registrado la asistencia. */
    public static boolean puedeCancelarAdmin(Reserva r) {
        return r.vigente() && r.getAsistencia() == Asistencia.PENDIENTE;
    }

    public static boolean puedeCobrarSaldo(Reserva r) {
        return r.getEstado() == EstadoReserva.CONFIRMADA && r.saldo().signum() > 0;
    }

    /** «Fútbol 5», «Multiusos (vóley y básquet)»… */
    public static String deporteTexto(String deporte) {
        return DEPORTES.getOrDefault(deporte, deporte);
    }

    /** Estado legible para la constancia y los avisos. */
    public static String estadoTexto(Reserva r) {
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            return "Cancelada";
        }
        if (r.getEstado() == EstadoReserva.CONFIRMADA) {
            return r.getEstadoPago() == EstadoPago.PAGADO_TOTAL ? "Confirmada · pagada" : "Confirmada";
        }
        return switch (r.getEstadoPago()) {
            case EN_REVISION -> "Pago en revisión";
            case OBSERVADO -> "Comprobante observado";
            default -> "Pendiente de pago";
        };
    }
}
