package pe.alcanfores.pagos.servicio.documento;

import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.TipoPago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Textos del servicio vendido: «Alquiler de Cancha 2 · Fútbol 6 · vie 20/11/2026 7:00 p. m.–8:00 p. m.» y, en el
 * comprobante, «… · Adelanto 50 %».
 */
public final class ConceptoReserva {

    private static final String[] DIAS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final int MAX_CONCEPTO = 160;

    private ConceptoReserva() {
    }

    public static String concepto(String nombreCancha, String deporte, LocalDate fecha, LocalTime inicio, LocalTime fin,
                                  String codigoReserva) {
        if (nombreCancha == null || nombreCancha.isBlank() || fecha == null || inicio == null || fin == null) {
            return "Alquiler de cancha deportiva · Reserva " + codigoReserva;
        }
        StringBuilder sb = new StringBuilder("Alquiler de ").append(nombreCancha.trim());
        String textoDeporte = deporteTexto(deporte);
        if (textoDeporte != null && !sinTildes(nombreCancha).contains(sinTildes(textoDeporte))) {
            sb.append(" · ").append(textoDeporte);
        }
        sb.append(" · ").append(DIAS[fecha.getDayOfWeek().getValue() - 1]).append(' ')
                .append(fecha.format(Tiempo.FECHA_CORTA)).append(' ')
                .append(Tiempo.horaAmPm(inicio)).append('–').append(Tiempo.horaAmPm(fin));
        return sb.length() > MAX_CONCEPTO ? sb.substring(0, MAX_CONCEPTO) : sb.toString();
    }

    /** Descripción del ítem del comprobante: concepto + qué parte se cobra. */
    public static String lineaComprobante(InstantaneaReserva reserva, TipoPago tipoPago) {
        String detalle = switch (tipoPago) {
            case ADELANTO -> reserva.getPorcentajeAdelanto() == null ? "Adelanto"
                    : "Adelanto " + reserva.getPorcentajeAdelanto() + " %";
            case TOTAL -> "Pago total";
            case SALDO -> "Saldo";
        };
        return reserva.getConcepto() + " · " + detalle;
    }

    /** Porcentaje del adelanto sobre el total (entero), o nulo si no se puede calcular. */
    public static Integer porcentaje(BigDecimal adelanto, BigDecimal total) {
        if (adelanto == null || total == null || total.signum() <= 0) {
            return null;
        }
        return adelanto.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }

    public static String deporteTexto(String deporte) {
        if (deporte == null) {
            return null;
        }
        return switch (deporte) {
            case "FUTBOL_5" -> "Fútbol 5";
            case "FUTBOL_6" -> "Fútbol 6";
            case "FUTBOL_7" -> "Fútbol 7";
            case "VOLEY" -> "Vóley";
            case "BASQUET" -> "Básquet";
            case "MULTIUSO" -> "Multiusos";
            default -> null;
        };
    }

    private static String sinTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
