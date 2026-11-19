package pe.alcanfores.notificaciones.modelo;

/**
 * PENDIENTE (creado, aún sin enviar) · ENVIADO (lo aceptó el SMTP o Evolution) · FALLIDO (error; se reintenta cada
 * 5 min mientras {@code intentos < 3}) · SIMULADO (el canal no está configurado: se guardó el cuerpo para verlo en el panel).
 */
public enum EstadoNotificacion {
    PENDIENTE,
    ENVIADO,
    FALLIDO,
    SIMULADO
}
