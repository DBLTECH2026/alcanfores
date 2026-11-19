package pe.alcanfores.comun.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Reloj único del sistema en America/Lima (RNF-13). En pruebas se puede fijar con {@link #fijar(Clock)}. */
public final class Tiempo {

    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    public static final Locale ES_PE = Locale.forLanguageTag("es-PE");
    public static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_PE);
    public static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_PE);
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ES_PE);
    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_PE);

    private static volatile Clock reloj = Clock.system(ZONA);

    private Tiempo() {
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(reloj).withNano(0);
    }

    public static LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public static LocalTime horaActual() {
        return LocalTime.now(reloj).withNano(0);
    }

    /** Solo para pruebas. */
    public static void fijar(Clock nuevo) {
        reloj = nuevo;
    }

    /** Solo para pruebas. */
    public static void restablecer() {
        reloj = Clock.system(ZONA);
    }

    /** «viernes 20 de noviembre de 2026» */
    public static String fechaLarga(LocalDate fecha) {
        return fecha.format(FECHA_LARGA);
    }

    /** «7:00 p. m.» */
    public static String horaAmPm(LocalTime hora) {
        int h = hora.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String sufijo = h < 12 ? "a. m." : "p. m.";
        return hora.getMinute() == 0 ? h12 + ":00 " + sufijo : String.format("%d:%02d %s", h12, hora.getMinute(), sufijo);
    }
}
