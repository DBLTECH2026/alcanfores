package pe.alcanfores.comun.util;

import java.util.Locale;

/** Normalización de textos que escribe el usuario. */
public final class Texto {

    private Texto() {
    }

    /** Quita espacios al inicio y al final y colapsa los dobles. */
    public static String limpiar(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    /** «rOSA  huamán» → «Rosa Huamán»; respeta tildes, ñ, apóstrofes y guiones. */
    public static String titulo(String s) {
        String limpio = limpiar(s);
        if (limpio == null || limpio.isEmpty()) {
            return limpio;
        }
        StringBuilder sb = new StringBuilder(limpio.length());
        boolean inicioPalabra = true;
        for (char c : limpio.toLowerCase(Tiempo.ES_PE).toCharArray()) {
            sb.append(inicioPalabra ? Character.toUpperCase(c) : c);
            inicioPalabra = c == ' ' || c == '-' || c == '\'';
        }
        return sb.toString();
    }

    /** «+51 987 654 321», «987-654-321» → «987654321». Devuelve el texto tal cual si no se puede normalizar. */
    public static String normalizarCelular(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("[\\s\\-()]", "");
        if (d.startsWith("+")) {
            d = d.substring(1);
        }
        if (d.length() == 11 && d.startsWith("51")) {
            d = d.substring(2);
        }
        return d;
    }

    /** Celular en formato internacional para WhatsApp: «51987654321». */
    public static String celularWhatsapp(String celular) {
        String d = normalizarCelular(celular);
        return d == null ? null : "51" + d;
    }

    public static String correo(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    /** «Diego Ramos Quispe» → «Diego R.» (autor de reseñas). */
    public static String autorCorto(String nombres, String apellidos) {
        String nombre = titulo(nombres == null ? "" : nombres).split(" ")[0];
        String ap = limpiar(apellidos == null ? "" : apellidos);
        return ap.isEmpty() ? nombre : nombre + " " + Character.toUpperCase(ap.charAt(0)) + ".";
    }
}
