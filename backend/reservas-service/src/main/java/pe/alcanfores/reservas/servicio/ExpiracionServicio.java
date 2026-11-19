package pe.alcanfores.reservas.servicio;

/** Compensación de la saga: cancela las reservas cuyo plazo para pagar venció y libera el turno. */
public interface ExpiracionServicio {

    /** @return cuántas reservas se expiraron */
    int expirarVencidas();
}
