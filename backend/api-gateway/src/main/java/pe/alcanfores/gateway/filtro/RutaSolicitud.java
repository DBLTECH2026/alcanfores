package pe.alcanfores.gateway.filtro;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Ruta «como la verá el servicio de destino»: sin parámetros de matriz ({@code ;x=1}), decodificada ({@code %2e})
 * y sin segmentos {@code ..}. Evita saltarse los filtros con trucos como {@code /api/reservas/..;/../interno/x}.
 */
public final class RutaSolicitud {

    private RutaSolicitud() {
    }

    public static String normalizar(String ruta) {
        if (ruta == null || ruta.isEmpty()) {
            return "/";
        }
        String actual = ruta;
        // Se decodifica hasta dos veces para cubrir la doble codificación (%252e).
        for (int i = 0; i < 2; i++) {
            actual = UriUtils.decode(actual.replaceAll(";[^/]*", ""), StandardCharsets.UTF_8);
        }
        String limpia = StringUtils.cleanPath(actual.replace('\\', '/'));
        limpia = limpia.replaceAll("/{2,}", "/");
        if (!limpia.startsWith("/")) {
            limpia = "/" + limpia;
        }
        // Los «..» que sobran al subir más allá de la raíz no llevan a ningún lado: el servidor los descarta.
        while (limpia.equals("/..") || limpia.startsWith("/../")) {
            limpia = limpia.length() <= 3 ? "/" : limpia.substring(3);
        }
        return limpia.toLowerCase(Locale.ROOT);
    }

    /** {@code true} si la ruta pide un prefijo (ej. {@code /interno}) o algo debajo de él. */
    public static boolean empiezaCon(String rutaNormalizada, String prefijo) {
        return rutaNormalizada.equals(prefijo) || rutaNormalizada.startsWith(prefijo + "/");
    }
}
