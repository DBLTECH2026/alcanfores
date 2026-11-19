package pe.alcanfores.notificaciones.modelo;

/**
 * Un archivo que acompaña al aviso (se guarda en la columna JSON {@code adjuntos}). No se guarda el archivo: se guarda
 * de dónde sacarlo ({@code referencia} = código de reserva, id de comprobante o nombre de la tarjeta) para poder
 * reintentar y para que el panel lo muestre.
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

    /** Tarjeta ilustrada {@code whatsapp/tarjetas/<nombre>.jpg}. */
    public static Adjunto tarjeta(String nombre) {
        return new Adjunto(TipoAdjunto.IMAGEN, nombre, nombre + ".jpg", EstadoAdjunto.PENDIENTE, null);
    }

    public Adjunto adjuntado() {
        return new Adjunto(tipo, referencia, nombreArchivo, EstadoAdjunto.ADJUNTADO, null);
    }

    public Adjunto noDisponible(String motivo) {
        return new Adjunto(tipo, referencia, nombreArchivo, EstadoAdjunto.NO_DISPONIBLE, motivo);
    }

    /** Leyenda breve del PDF en WhatsApp: «Constancia ALC-7K3M9P», «Boleta B001-00000012», «Nota de crédito BC01-…». */
    public String leyenda() {
        if (tipo == TipoAdjunto.CONSTANCIA) {
            return "Constancia " + referencia;
        }
        String serie = nombreArchivo.endsWith(".pdf") ? nombreArchivo.substring(0, nombreArchivo.length() - 4) : nombreArchivo;
        if (tipo == TipoAdjunto.COMPROBANTE) {
            if (serie.startsWith("BC") || serie.startsWith("FC")) {
                return "Nota de crédito " + serie;
            }
            return (serie.startsWith("F") ? "Factura " : "Boleta ") + serie;
        }
        return serie;
    }
}
