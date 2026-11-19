package pe.alcanfores.comun.util;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Textos de horario en 12 h para clientes y administradores (sin jerga): «7:00–9:00 p. m.», «11:00 a. m.–1:00 p. m.»,
 * «9:00–10:00 a. m. y 7:00–8:00 p. m.». El fin del día se guarda como 23:59:59 (una columna TIME no admite 24:00) y se
 * muestra «12:00 a. m.».
 */
public final class Horarios {

    /** Cómo se guarda «12:00 a. m.» de cierre (fin del día). */
    public static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59);

    private Horarios() {
    }

    /** Un turno o tramo de horario. */
    public record Tramo(LocalTime inicio, LocalTime fin) {
    }

    public static boolean esFinDelDia(LocalTime hora) {
        return hora != null && !hora.isBefore(FIN_DEL_DIA);
    }

    /** Minutos desde las 00:00; el fin del día cuenta como 1440. */
    public static int minutos(LocalTime hora) {
        return esFinDelDia(hora) ? 24 * 60 : hora.getHour() * 60 + hora.getMinute();
    }

    /** Inverso de {@link #minutos}: 1440 → 23:59:59. */
    public static LocalTime hora(int minutos) {
        return minutos >= 24 * 60 ? FIN_DEL_DIA : LocalTime.of(minutos / 60, minutos % 60);
    }

    /**
     * «7:00–9:00 p. m.» si ambas horas son del mismo lado del mediodía; si no (o si termina a medianoche), la forma completa:
     * «11:00 a. m.–1:00 p. m.», «7:00 a. m.–12:00 a. m.».
     */
    public static String rango(LocalTime inicio, LocalTime fin) {
        String a = Tiempo.horaAmPm(inicio);
        String b = Tiempo.horaAmPm(fin);
        String sufijoA = a.substring(a.indexOf(' ') + 1);
        String sufijoB = b.substring(b.indexOf(' ') + 1);
        boolean acortar = sufijoA.equals(sufijoB) && !esFinDelDia(fin) && !LocalTime.MIDNIGHT.equals(fin);
        return acortar ? a.substring(0, a.indexOf(' ')) + "–" + b : a + "–" + b;
    }

    /**
     * Texto de varios turnos: une los seguidos en un solo tramo y separa los demás con comas e «y».
     * Ej.: [19:00–20:00, 20:00–21:00] → «7:00–9:00 p. m.»; [9–10, 19–20] → «9:00–10:00 a. m. y 7:00–8:00 p. m.».
     */
    public static String texto(List<Tramo> turnos) {
        if (turnos == null || turnos.isEmpty()) {
            return "";
        }
        List<Tramo> ordenados = new ArrayList<>(turnos);
        ordenados.sort(Comparator.comparing(Tramo::inicio));
        List<Tramo> unidos = new ArrayList<>();
        for (Tramo t : ordenados) {
            int ultimo = unidos.size() - 1;
            if (ultimo >= 0 && minutos(unidos.get(ultimo).fin()) == minutos(t.inicio())) {
                unidos.set(ultimo, new Tramo(unidos.get(ultimo).inicio(), t.fin()));
            } else {
                unidos.add(t);
            }
        }
        List<String> partes = unidos.stream().map(t -> rango(t.inicio(), t.fin())).toList();
        if (partes.size() == 1) {
            return partes.get(0);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }
}
