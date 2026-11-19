package pe.alcanfores.pagos.servicio;

import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;

/** Compensación de la saga cuando reservas-service cancela una reserva. */
public interface CancelacionReservaServicio {

    /**
     * Pagos PENDIENTE → ANULADO e intentos abiertos → EXPIRADO; si hay {@code devolucion} y cobros VALIDADOS → REEMBOLSADO
     * (simulado) y una nota de crédito por cada comprobante. Idempotente por {@code idEvento}.
     *
     * @return {@code false} si el evento ya se había procesado.
     */
    boolean procesar(ReservaCanceladaEvento evento);
}
