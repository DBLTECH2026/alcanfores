package pe.alcanfores.catalogo.util;

import pe.alcanfores.catalogo.modelo.HorarioCancha;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Horas del horario de las canchas. En JSON van como {@code "HH:mm"}; el cierre a medianoche se recibe como "24:00" o
 * "23:59", se guarda 23:59:59 y se devuelve "24:00". Además arma el resumen «Lun a vie 8:00 a. m.–11:00 p. m. · Dom cerrado».
 */
public final class Horas {

    private static final String[] DIAS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    private Horas() {
    }

    /** «08:00»; el fin del día sale «24:00». */
    public static String formato(LocalTime hora) {
        if (hora == null) {
            return null;
        }
        return Horarios.esFinDelDia(hora) ? "24:00" : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }

    /** Lee «HH:mm» ya validado; «24:00» y «23:59» son el fin del día (23:59:59). Nulo o vacío → nulo. */
    public static LocalTime leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim();
        if ("24:00".equals(t) || "23:59".equals(t)) {
            return Horarios.FIN_DEL_DIA;
        }
        return LocalTime.parse(t);
    }

    /** Minutos de inicio de cada turno: de la apertura en pasos de {@code duracion} mientras el turno termine antes del cierre. */
    public static List<Integer> inicios(LocalTime apertura, LocalTime cierre, int duracion) {
        List<Integer> inicios = new ArrayList<>();
        if (apertura == null || cierre == null || duracion <= 0) {
            return inicios;
        }
        int fin = Horarios.minutos(cierre);
        for (int m = Horarios.minutos(apertura); m + duracion <= fin; m += duracion) {
            inicios.add(m);
        }
        return inicios;
    }

    /**
     * Resumen de la semana uniendo días SEGUIDOS con el mismo horario: «Todos los días 8:00 a. m.–11:00 p. m.»,
     * «Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–11:00 p. m.», «… · Dom cerrado».
     */
    public static String resumen(List<HorarioCancha> semana) {
        if (semana == null || semana.isEmpty()) {
            return "Sin horario";
        }
        List<HorarioCancha> dias = new ArrayList<>(semana);
        dias.sort((a, b) -> Integer.compare(a.getDiaSemana(), b.getDiaSemana()));
        if (dias.stream().noneMatch(HorarioCancha::isAbierto)) {
            return "Cerrada todos los días";
        }
        List<String> partes = new ArrayList<>();
        int i = 0;
        while (i < dias.size()) {
            int j = i;
            while (j + 1 < dias.size() && dias.get(j + 1).getDiaSemana() == dias.get(j).getDiaSemana() + 1
                    && mismoHorario(dias.get(i), dias.get(j + 1))) {
                j++;
            }
            HorarioCancha h = dias.get(i);
            // Siempre la forma completa: «7:00 a. m.–12:00 a. m.» (la corta «7:00–12:00 a. m.» confunde con la medianoche).
            String horario = h.isAbierto() ? Tiempo.horaAmPm(h.getApertura()) + "–" + Tiempo.horaAmPm(h.getCierre()) : "cerrado";
            if (j - i + 1 == 7) {
                return "Todos los días " + horario;
            }
            partes.add(nombreDias(dias.get(i).getDiaSemana(), dias.get(j).getDiaSemana()) + " " + horario);
            i = j + 1;
        }
        return String.join(" · ", partes);
    }

    private static boolean mismoHorario(HorarioCancha a, HorarioCancha b) {
        if (a.isAbierto() != b.isAbierto()) {
            return false;
        }
        return !a.isAbierto() || (Objects.equals(a.getApertura(), b.getApertura()) && Objects.equals(a.getCierre(), b.getCierre()));
    }

    /** «Lun», «Sáb y dom», «Lun a vie». */
    private static String nombreDias(int desde, int hasta) {
        String primero = DIAS[desde - 1];
        primero = Character.toUpperCase(primero.charAt(0)) + primero.substring(1);
        if (desde == hasta) {
            return primero;
        }
        return primero + (hasta == desde + 1 ? " y " : " a ") + DIAS[hasta - 1];
    }
}
