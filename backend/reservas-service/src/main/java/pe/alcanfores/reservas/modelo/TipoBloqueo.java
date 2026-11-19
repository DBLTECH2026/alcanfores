package pe.alcanfores.reservas.modelo;

/**
 * Por qué está bloqueado un turno. MANUAL lo pone el administrador (se respeta al regenerar); los demás los pone el
 * sistema y los quita solo cuando cambia su causa. FUERA_DE_HORARIO: turno libre que ya no calza con el horario de la
 * cancha y no se pudo borrar (lo referencia una reserva cancelada): no se muestra en disponibilidad ni en la agenda.
 */
public enum TipoBloqueo {
    MANUAL, FERIADO, NO_LABORABLE, CANCHA_INACTIVA, FUERA_DE_HORARIO
}
