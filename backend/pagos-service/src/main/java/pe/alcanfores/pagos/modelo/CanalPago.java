package pe.alcanfores.pagos.modelo;

/** Cómo llegó el pago: captura de Yape/Plin, pasarela en línea o cobro presencial en caja. */
public enum CanalPago {
    COMPROBANTE, PASARELA, CAJA
}
