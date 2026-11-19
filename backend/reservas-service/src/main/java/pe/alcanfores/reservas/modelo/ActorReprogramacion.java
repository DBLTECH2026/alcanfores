package pe.alcanfores.reservas.modelo;

/**
 * Quién cambió el horario de la reserva: el cliente desde el portal, Canchita (asistente con IA, llamada interna con
 * su conversación) o el panel (ADMIN o RECEPCION). El cliente y Canchita comparten el límite de un cambio por reserva.
 */
public enum ActorReprogramacion {
    CLIENTE, CANCHITA, PANEL;

    /** Cliente o Canchita: aplican el plazo y el límite de cambios del portal. */
    public boolean delCliente() {
        return this != PANEL;
    }
}
