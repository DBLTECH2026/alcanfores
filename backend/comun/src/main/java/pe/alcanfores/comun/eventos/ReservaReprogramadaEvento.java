package pe.alcanfores.comun.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * reserva.reprogramada · la publica reservas cuando una reserva vigente cambia de fecha, hora o cancha conservando lo
 * pagado. {@code reserva} son los datos DESPUÉS del cambio (nuevo total, nuevo adelanto y lo pagado); los campos
 * {@code *Anterior} describen el horario que tenía. {@code estado} (PENDIENTE o CONFIRMADA) le dice a notificaciones si
 * debe volver a programar los recordatorios; {@code estadoPago} (SIN_PAGO, EN_REVISION, OBSERVADO, ADELANTO_PAGADO o
 * PAGADO_TOTAL) y {@code venceEn} (plazo para pagar; null si no hay) eligen qué decirle al cliente de su pago.
 * {@code actor}: CLIENTE (portal), CANCHITA (asistente con IA) o PANEL; {@code motivo} solo viene cuando cambia el panel
 * y es una nota INTERNA (no se le muestra al cliente).
 */
public record ReservaReprogramadaEvento(UUID idEvento, LocalDateTime ocurridoEn, DatosReserva reserva, String estado,
                                        BigDecimal saldoPendiente, String canchaAnterior, LocalDate fechaAnterior,
                                        LocalTime horaInicioAnterior, LocalTime horaFinAnterior,
                                        String horarioTextoAnterior, BigDecimal montoTotalAnterior, String actor,
                                        String motivo, String estadoPago, LocalDateTime venceEn) implements EventoDominio {

    public static final String CLIENTE = "CLIENTE";
    public static final String CANCHITA = "CANCHITA";
    public static final String PANEL = "PANEL";

    /** La reserva quedó confirmada (tiene recordatorios que mover). */
    public boolean confirmada() {
        return "CONFIRMADA".equals(estado);
    }

    /** Pendiente con un comprobante que el complejo aún está validando (no hay que pedirle que pague otra vez). */
    public boolean pagoEnRevision() {
        return !confirmada() && "EN_REVISION".equals(estadoPago);
    }
}
