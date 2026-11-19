package pe.alcanfores.notificaciones.modelo;

/**
 * Un PDF que acompaña al aviso (se guarda en la columna JSON {@code adjuntos}). No se guarda el archivo: se guarda
 * de dónde sacarlo ({@code referencia} = código de reserva o id de comprobante) para poder reintentar.
 */
public record Adjunto(TipoAdjunto tipo, String referencia, String nombreArchivo, EstadoAdjunto estado, String detalle) {

    public static Adjunto constancia(String codigoReserva) {
        return new Adjunto(TipoAdjunto.CONSTANCIA, codigoReserva, "constancia-" + codigoReserva + ".pdf",
                EstadoAdjunto.PENDIENTE, null);
    }

    public static Adjunto comprobante(Long idComprobante, String serieNumero) {
        return new Adjunto(TipoAdjunto.COMPROBANTE, String.valueOf(idComprobante), serieNumero + ".pdf",
                EstadoAdjunto.PENDIENTE, null);
    }

    public Adjunto adjuntado() {
        return new Adjunto(tipo, referencia, nombreArchivo, EstadoAdjunto.ADJUNTADO, null);
    }

    public Adjunto noDisponible(String motivo) {
        return new Adjunto(tipo, referencia, nombreArchivo, EstadoAdjunto.NO_DISPONIBLE, motivo);
    }
}
