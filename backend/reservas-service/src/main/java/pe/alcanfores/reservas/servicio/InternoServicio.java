package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.dto.InternoDtos.ElegibilidadResena;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaInterna;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaSemilla;

import java.time.LocalDateTime;
import java.util.List;

/** Consultas para otros microservicios (rutas {@code /interno/**}). */
public interface InternoServicio {

    ReservaInterna reserva(String codigo);

    /** ¿Puede este DNI reseñar esta cancha con esta reserva? (catalogo-service). */
    ElegibilidadResena elegibleResena(String codigo, String dni, Long idCancha);

    byte[] constancia(String codigo);

    /** Reservas CONFIRMADAS que empiezan entre {@code desde} y {@code hasta} (notificaciones-service). */
    List<ReservaInterna> proximas(LocalDateTime desde, LocalDateTime hasta);

    /** Reservas creadas por la semilla (pagos-service siembra pagos y comprobantes coherentes). */
    List<ReservaSemilla> paraSemilla();
}
