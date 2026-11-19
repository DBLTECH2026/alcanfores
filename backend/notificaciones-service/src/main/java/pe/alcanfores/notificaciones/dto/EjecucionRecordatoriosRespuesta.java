package pe.alcanfores.notificaciones.dto;

/** Resultado de una pasada del job de recordatorios (cron o botón «ejecutar ahora» del panel). */
public record EjecucionRecordatoriosRespuesta(int revisados, int enviados, int omitidos, int cancelados) {
}
