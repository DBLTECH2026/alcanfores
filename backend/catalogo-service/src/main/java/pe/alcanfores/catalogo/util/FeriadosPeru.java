package pe.alcanfores.catalogo.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Feriados nacionales de Perú (calendario oficial vigente): fijos más Jueves y Viernes Santo, que dependen de la Pascua.
 */
public final class FeriadosPeru {

    /** Un feriado del año. */
    public record Feriado(LocalDate fecha, String nombre) {
    }

    private FeriadosPeru() {
    }

    public static List<Feriado> del(int anio) {
        LocalDate pascua = pascua(anio);
        List<Feriado> lista = new ArrayList<>();
        lista.add(new Feriado(LocalDate.of(anio, 1, 1), "Año Nuevo"));
        lista.add(new Feriado(pascua.minusDays(3), "Jueves Santo"));
        lista.add(new Feriado(pascua.minusDays(2), "Viernes Santo"));
        lista.add(new Feriado(LocalDate.of(anio, 5, 1), "Día del Trabajo"));
        lista.add(new Feriado(LocalDate.of(anio, 6, 7), "Batalla de Arica y Día de la Bandera"));
        lista.add(new Feriado(LocalDate.of(anio, 6, 29), "San Pedro y San Pablo"));
        lista.add(new Feriado(LocalDate.of(anio, 7, 23), "Día de la Fuerza Aérea del Perú"));
        lista.add(new Feriado(LocalDate.of(anio, 7, 28), "Fiestas Patrias"));
        lista.add(new Feriado(LocalDate.of(anio, 7, 29), "Fiestas Patrias"));
        lista.add(new Feriado(LocalDate.of(anio, 8, 6), "Batalla de Junín"));
        lista.add(new Feriado(LocalDate.of(anio, 8, 30), "Santa Rosa de Lima"));
        lista.add(new Feriado(LocalDate.of(anio, 10, 8), "Combate de Angamos"));
        lista.add(new Feriado(LocalDate.of(anio, 11, 1), "Día de Todos los Santos"));
        lista.add(new Feriado(LocalDate.of(anio, 12, 8), "Inmaculada Concepción"));
        lista.add(new Feriado(LocalDate.of(anio, 12, 9), "Batalla de Ayacucho"));
        lista.add(new Feriado(LocalDate.of(anio, 12, 25), "Navidad"));
        return lista;
    }

    /** Domingo de Pascua (algoritmo anónimo gregoriano de Meeus/Jones/Butcher). */
    public static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
