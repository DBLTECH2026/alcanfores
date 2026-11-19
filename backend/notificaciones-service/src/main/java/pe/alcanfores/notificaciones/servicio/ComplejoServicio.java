package pe.alcanfores.notificaciones.servicio;

/** Datos del complejo para los mensajes, leídos de catalogo-service con caché y valores de respaldo. */
public interface ComplejoServicio {

    DatosComplejo datos();

    /**
     * Vuelve a leer el catálogo sin esperar la caché (si no responde, usa lo último conocido). Se usa al confirmar una
     * reserva: así un cambio de {@code recordatorio_minutos} aplica a las reservas que se confirmen desde ese momento.
     */
    DatosComplejo actualizados();
}
