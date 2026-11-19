package pe.alcanfores.comun.eventos;

import java.time.LocalTime;

/**
 * Horario de una cancha en un día de la semana (1 = lunes … 7 = domingo, ISO). {@code cierre} es la hora en que TERMINA el
 * último turno; «12:00 a. m.» (fin del día) se guarda como 23:59:59. Si {@code abierto} es falso, apertura y cierre se ignoran.
 */
public record HorarioDia(int diaSemana, boolean abierto, LocalTime apertura, LocalTime cierre) {
}
