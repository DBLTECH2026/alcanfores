package pe.alcanfores.reservas.cliente;

import pe.alcanfores.comun.eventos.HorarioDia;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.reservas.modelo.Franja;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Cancha tal como la publica catalogo-service ({@code GET /interno/canchas} o el evento {@code cancha.actualizada}), con su
 * horario propio. Los campos de horario pueden llegar nulos (catálogo antiguo o sin datos): se usan entonces los valores
 * por defecto del complejo (primer turno, cierre, duración e inicio de la noche globales) y feriado ABIERTA con S/ 10.
 */
public record CanchaCatalogo(Long id, String nombre, String deporte, BigDecimal precioDia, BigDecimal precioNoche,
                             String estado, Integer duracionTurnoMin, LocalTime inicioNoche, List<HorarioDia> dias,
                             String feriadoModo, BigDecimal recargoFeriado) {

    public static final String ACTIVA = "ACTIVA";
    public static final String CERRADA = "CERRADA";
    public static final BigDecimal RECARGO_POR_DEFECTO = new BigDecimal("10.00");

    /** Cancha sin horario propio (usa el del complejo). */
    public CanchaCatalogo(Long id, String nombre, String deporte, BigDecimal precioDia, BigDecimal precioNoche, String estado) {
        this(id, nombre, deporte, precioDia, precioNoche, estado, null, null, null, null, null);
    }

    /** Las 4 canchas semilla del SPEC §5.2 (si catálogo no responde al arrancar). */
    public static List<CanchaCatalogo> semilla() {
        return List.of(
                new CanchaCatalogo(1L, "Cancha 1 · Fútbol 5", "FUTBOL_5", new BigDecimal("60.00"), new BigDecimal("80.00"), ACTIVA),
                new CanchaCatalogo(2L, "Cancha 2 · Fútbol 6", "FUTBOL_6", new BigDecimal("70.00"), new BigDecimal("90.00"), ACTIVA),
                new CanchaCatalogo(3L, "Cancha 3 · Fútbol 7", "FUTBOL_7", new BigDecimal("90.00"), new BigDecimal("120.00"), ACTIVA),
                new CanchaCatalogo(4L, "Losa multiusos", "MULTIUSO", new BigDecimal("40.00"), new BigDecimal("50.00"), ACTIVA));
    }

    public boolean activa() {
        return estado == null || ACTIVA.equalsIgnoreCase(estado);
    }

    /** Precio base (sin recargo de feriado) de la franja. */
    public BigDecimal precio(Franja franja) {
        BigDecimal p = franja == Franja.NOCHE ? precioNoche : precioDia;
        return p.setScale(2, RoundingMode.HALF_UP);
    }

    public int duracion(ParametrosComplejo p) {
        return duracionTurnoMin == null || duracionTurnoMin < 15 ? p.duracionMin() : duracionTurnoMin;
    }

    public LocalTime noche(ParametrosComplejo p) {
        return inicioNoche == null ? p.inicioNoche() : inicioNoche;
    }

    /** Un turno que EMPIEZA a la hora de noche o después cobra la tarifa de noche. */
    public Franja franja(LocalTime horaInicio, ParametrosComplejo p) {
        return horaInicio.isBefore(noche(p)) ? Franja.DIA : Franja.NOCHE;
    }

    public boolean cerradaEnFeriado() {
        return CERRADA.equalsIgnoreCase(feriadoModo);
    }

    public BigDecimal recargo() {
        return (recargoFeriado == null ? RECARGO_POR_DEFECTO : recargoFeriado).setScale(2, RoundingMode.HALF_UP);
    }

    /** Horario del día de la semana; sin horario propio, el del complejo (todos los días). */
    public HorarioDia horario(DayOfWeek dia, ParametrosComplejo p) {
        if (dias != null) {
            for (HorarioDia h : dias) {
                if (h != null && h.diaSemana() == dia.getValue()) {
                    return h;
                }
            }
        }
        return new HorarioDia(dia.getValue(), true, p.primerTurno(), p.ultimoTurno());
    }

    /** Turnos [inicio, fin) del día según su horario (vacío si ese día no abre). El último termina en el cierre o antes. */
    public List<Horarios.Tramo> tramos(DayOfWeek dia, ParametrosComplejo p) {
        HorarioDia h = horario(dia, p);
        if (!h.abierto() || h.apertura() == null || h.cierre() == null) {
            return List.of();
        }
        int duracion = duracion(p);
        int inicio = Horarios.minutos(h.apertura());
        int cierre = minutosCierre(h.cierre());
        java.util.ArrayList<Horarios.Tramo> tramos = new java.util.ArrayList<>();
        for (int m = inicio; m + duracion <= cierre; m += duracion) {
            tramos.add(new Horarios.Tramo(Horarios.hora(m), Horarios.hora(m + duracion)));
        }
        return tramos;
    }

    /** El cierre a medianoche puede llegar como 00:00, 23:59 o 23:59:59: todos valen 24:00. */
    static int minutosCierre(LocalTime cierre) {
        if (cierre.equals(LocalTime.MIDNIGHT) || !cierre.isBefore(LocalTime.of(23, 59))) {
            return 24 * 60;
        }
        return Horarios.minutos(cierre);
    }

    /** Mismos datos que importan para los turnos (nombre, deporte, precios, estado y horario). */
    public boolean mismosDatos(CanchaCatalogo o) {
        return o != null && Objects.equals(nombre, o.nombre) && Objects.equals(deporte, o.deporte)
                && precioDia.compareTo(o.precioDia) == 0 && precioNoche.compareTo(o.precioNoche) == 0
                && activa() == o.activa() && Objects.equals(duracionTurnoMin, o.duracionTurnoMin)
                && Objects.equals(inicioNoche, o.inicioNoche) && Objects.equals(dias, o.dias)
                && cerradaEnFeriado() == o.cerradaEnFeriado() && recargo().compareTo(o.recargo()) == 0;
    }
}
