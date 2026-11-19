package pe.alcanfores.pagos.servicio;

import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;

/** Lo que hace pagos-service cuando reservas-service cambia la fecha, hora o cancha de una reserva. */
public interface ReprogramacionReservaServicio {

    /**
     * Intentos de pago en línea ABIERTOS de ese código (pasarela beta y Mercado Pago) creados ANTES del cambio
     * ({@code creadoEn <= ocurridoEn}) → EXPIRADO: con {@link AprobacionEnLinea#MOTIVO_HORARIO} si su monto ya no es el
     * de un intento nuevo (nadie paga un monto viejo) o con {@link AprobacionEnLinea#MOTIVO_CAMBIO_HORARIO} si el
     * horario nuevo cuesta lo mismo. Un intento creado después del cambio (evento atrasado) o que ya tiene el monto y el
     * concepto del horario nuevo queda abierto. Pagos PENDIENTE (comprobante en revisión) con la fotografía del horario
     * nuevo (la boleta que se emita al validarlos sale con él). Los pagos ya validados y sus comprobantes no se tocan.
     * Idempotente por {@code idEvento}.
     *
     * @return {@code false} si el evento ya se había procesado.
     */
    boolean procesar(ReservaReprogramadaEvento evento);
}
