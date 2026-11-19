package pe.alcanfores.reservas.servicio;

import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;

/** Reacciones (idempotentes) a los eventos de pagos-service: parte de la saga coreografiada de la reserva. */
public interface PagoEventosServicio {

    /** EN_REVISION y sin plazo (el cliente ya mandó su comprobante). */
    void alRegistrarPago(PagoRegistradoEvento evento);

    /** ADELANTO→ADELANTO_PAGADO, TOTAL→PAGADO_TOTAL, SALDO suma; ADELANTO/TOTAL confirman y publican reserva.confirmada. */
    void alValidarPago(PagoValidadoEvento evento);

    /** OBSERVADO con nuevo plazo {@code min(ahora + 12 h, inicio − 1 h)}. */
    void alRechazarPago(PagoRechazadoEvento evento);
}
