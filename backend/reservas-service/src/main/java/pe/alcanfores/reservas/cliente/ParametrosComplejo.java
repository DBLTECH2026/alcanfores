package pe.alcanfores.reservas.cliente;

import pe.alcanfores.reservas.modelo.Franja;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglas y datos del complejo que llegan de catalogo-service ({@code GET /interno/configuracion}). Si catálogo no responde
 * se usan los valores por defecto del SPEC (§5.2) para no bloquear el servicio. {@code asistenciaDesdeMin}: desde cuántos
 * minutos antes del inicio se puede marcar la asistencia (clave {@code asistencia_desde_min}, 0 a 180; 30 por defecto).
 */
public record ParametrosComplejo(int adelantoPct, int devolucionHoras, LocalTime primerTurno, LocalTime ultimoTurno,
                                 int duracionMin, LocalTime inicioNoche, int diasVisibles, int minutosParaPagar,
                                 int asistenciaDesdeMin, String nombreComplejo, String direccion, String referencia,
                                 String whatsapp, String correo, String mapaUrl, int diasGenerados) {

    private static final ParametrosComplejo DEFECTO = new ParametrosComplejo(50, 24, LocalTime.of(8, 0), LocalTime.of(23, 0),
            60, LocalTime.of(18, 0), 14, 20, 30, "Complejo Deportivo Los Alcanfores",
            "Carretera Central km 11.5, Santa Clara, Ate",
            "Paradero Caseta, a dos cuadras de la UTP Santa Clara",
            "+51 914 995 187", "informes.alcanfores@gmail.com",
            "https://www.google.com/maps/search/?api=1&query=Complejo+Deportivo+Los+Alcanfores+Santa+Clara+Ate", 120);

    public static ParametrosComplejo porDefecto() {
        return DEFECTO;
    }

    /** Construye los parámetros desde el mapa clave/valor de catálogo (acepta claves snake_case o camelCase). */
    public static ParametrosComplejo desde(Map<String, ?> mapa) {
        Map<String, String> m = new HashMap<>();
        if (mapa != null) {
            mapa.forEach((k, v) -> {
                if (k != null && v != null) {
                    m.put(k.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT), String.valueOf(v).trim());
                }
            });
        }
        ParametrosComplejo d = DEFECTO;
        return new ParametrosComplejo(
                entero(m, "adelantopct", d.adelantoPct, 0, 100),
                entero(m, "devolucionhoras", d.devolucionHoras, 0, 720),
                hora(m, "primerturno", d.primerTurno),
                hora(m, "ultimoturno", d.ultimoTurno),
                entero(m, "duracionmin", d.duracionMin, 30, 180),
                hora(m, "inicionoche", d.inicioNoche),
                entero(m, "diasvisibles", d.diasVisibles, 1, 60),
                entero(m, "minutosparapagar", d.minutosParaPagar, 5, 1440),
                entero(m, "asistenciadesdemin", d.asistenciaDesdeMin, 0, 180),
                texto(m, "nombrecomplejo", d.nombreComplejo),
                texto(m, "direccion", d.direccion),
                texto(m, "referencia", d.referencia),
                texto(m, "whatsapp", d.whatsapp),
                texto(m, "correo", d.correo),
                texto(m, "mapaurl", d.mapaUrl),
                entero(m, "diasgenerados", d.diasGenerados, 14, 365));
    }

    /** Horas de inicio de los turnos del día: desde el primer turno hasta que el siguiente ya no termine antes del cierre. */
    public List<LocalTime> horasDeInicio() {
        int inicio = primerTurno.toSecondOfDay() / 60;
        int cierre = ultimoTurno.equals(LocalTime.MIDNIGHT) ? 24 * 60 : ultimoTurno.toSecondOfDay() / 60;
        List<LocalTime> horas = new ArrayList<>();
        for (int m = inicio; m + duracionMin <= cierre; m += duracionMin) {
            horas.add(LocalTime.of(m / 60, m % 60));
        }
        return horas;
    }

    /** Días hacia adelante con turnos generados (nunca menos que los visibles). */
    public int horizonte() {
        return Math.max(diasGenerados, diasVisibles);
    }

    public Franja franja(LocalTime horaInicio) {
        return horaInicio.isBefore(inicioNoche) ? Franja.DIA : Franja.NOCHE;
    }

    private static int entero(Map<String, String> m, String clave, int defecto, int min, int max) {
        try {
            int v = (int) Double.parseDouble(m.getOrDefault(clave, String.valueOf(defecto)));
            return v < min || v > max ? defecto : v;
        } catch (NumberFormatException e) {
            return defecto;
        }
    }

    private static LocalTime hora(Map<String, String> m, String clave, LocalTime defecto) {
        String v = m.get(clave);
        if (v == null || v.isBlank()) {
            return defecto;
        }
        try {
            String limpio = v.length() == 4 ? "0" + v : v;
            return LocalTime.parse(limpio.length() > 5 ? limpio.substring(0, 5) : limpio);
        } catch (RuntimeException e) {
            return defecto;
        }
    }

    private static String texto(Map<String, String> m, String clave, String defecto) {
        String v = m.get(clave);
        return v == null || v.isBlank() ? defecto : v;
    }
}
