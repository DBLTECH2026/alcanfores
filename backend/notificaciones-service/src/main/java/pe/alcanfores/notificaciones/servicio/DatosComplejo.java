package pe.alcanfores.notificaciones.servicio;

/** Datos del complejo que van en los mensajes (dirección, mapa, WhatsApp) y reglas que afectan a los avisos. */
public record DatosComplejo(String nombre, String direccion, String referencia, String mapaUrl, String whatsapp,
                            String correo, int recordatorioHoras, int devolucionHoras) {

    /** «914995187» → «914 995 187». */
    public String whatsappTexto() {
        String w = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
        if (w.length() == 11 && w.startsWith("51")) {
            w = w.substring(2);
        }
        return w.length() == 9 ? w.substring(0, 3) + " " + w.substring(3, 6) + " " + w.substring(6) : w;
    }

    /** Enlace directo al chat: {@code https://wa.me/51914995187}. */
    public String whatsappUrl() {
        return "https://wa.me/51" + whatsappTexto().replace(" ", "");
    }
}
