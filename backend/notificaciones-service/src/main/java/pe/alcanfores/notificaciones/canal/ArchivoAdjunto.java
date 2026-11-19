package pe.alcanfores.notificaciones.canal;

/**
 * Archivo listo para enviar: la tarjeta ilustrada (JPG) o un PDF (constancia o comprobante).
 *
 * @param leyenda texto breve que acompaña al PDF en WhatsApp («Constancia ALC-7K3M9P», «Boleta B001-00000012»)
 */
public record ArchivoAdjunto(String nombre, byte[] contenido, String mime, String leyenda) {

    public static final String MIME_JPG = "image/jpeg";
    public static final String MIME_PDF = "application/pdf";

    public boolean imagen() {
        return MIME_JPG.equals(mime);
    }
}
