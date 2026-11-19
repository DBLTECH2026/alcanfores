package pe.alcanfores.notificaciones.servicio;

import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;

import java.time.LocalDate;

/** Filtros de la bandeja: todos opcionales. {@code q} busca en código de reserva, destino y asunto. */
public record FiltroNotificaciones(CanalNotificacion canal, EstadoNotificacion estado, String evento, String q,
                                   LocalDate desde, LocalDate hasta) {
}
