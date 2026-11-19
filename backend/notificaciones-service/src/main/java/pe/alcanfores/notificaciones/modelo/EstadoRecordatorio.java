package pe.alcanfores.notificaciones.modelo;

/** PROGRAMADO → ENVIADO; o CANCELADO (se canceló la reserva) u OMITIDO (faltaban menos de 30 min al revisarlo). */
public enum EstadoRecordatorio {
    PROGRAMADO,
    ENVIADO,
    CANCELADO,
    OMITIDO
}
