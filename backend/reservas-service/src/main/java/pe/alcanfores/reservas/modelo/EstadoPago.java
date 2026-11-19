package pe.alcanfores.reservas.modelo;

/**
 * Estado del pago de la reserva. {@code REEMBOLSADO}: la reserva se canceló con devolución y lo cobrado se devolvió
 * (pagos-service lo reembolsa y emite la nota de crédito al consumir {@code reserva.cancelada}).
 */
public enum EstadoPago {
    SIN_PAGO, EN_REVISION, OBSERVADO, ADELANTO_PAGADO, PAGADO_TOTAL, REEMBOLSADO
}
