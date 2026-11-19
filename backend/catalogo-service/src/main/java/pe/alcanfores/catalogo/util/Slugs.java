package pe.alcanfores.catalogo.util;

import java.text.Normalizer;
import java.util.Locale;

/** «Cancha 1 · Fútbol 5» → «cancha-1-futbol-5» (para URLs amigables del portal). */
public final class Slugs {

    static final int MAXIMO = 70;

    private Slugs() {
    }

    public static String de(String texto) {
        String base = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('ñ', 'n')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.length() > MAXIMO) {
            base = base.substring(0, MAXIMO).replaceAll("-+$", "");
        }
        return base.isEmpty() ? "cancha" : base;
    }
}
