package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.Reserva;

/** Constancia de reserva en PDF A4 (logo, código grande, QR, cancha, fecha, hora, montos, estado y condiciones). */
public interface ConstanciaServicio {

    /**
     * @param cancelacion la cancelación si la reserva está CANCELADA (o {@code null})
     * @return los bytes del PDF
     */
    byte[] generar(Reserva reserva, Cancelacion cancelacion);
}
