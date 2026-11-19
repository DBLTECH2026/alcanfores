package pe.alcanfores.notificaciones.plantilla;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tarjetas ilustradas de los avisos ({@code resources/whatsapp/tarjetas/<nombre>.jpg}, 1200×800, ~90 KB). Se leen una
 * vez y quedan en memoria. Imagen del WhatsApp, cabecera del correo (CID {@value #CID}) y vista previa del panel.
 */
@Component
public class Tarjetas {

    public static final String CID = "tarjeta-aviso";
    static final String CARPETA = "whatsapp/tarjetas/";
    private static final Pattern NOMBRE = Pattern.compile("^[a-z0-9-]{1,40}$");

    private final Map<String, Optional<byte[]>> cache = new ConcurrentHashMap<>();

    /** Bytes del JPG, o vacío si el nombre no es válido o la tarjeta no existe. */
    public Optional<byte[]> leer(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(nombre, Tarjetas::cargar);
    }

    /** {@code data:image/jpeg;base64,...} para mostrarla en el panel sin otra petición. */
    public Optional<String> dataUri(String nombre) {
        return leer(nombre).map(b -> "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(b));
    }

    private static Optional<byte[]> cargar(String nombre) {
        ClassPathResource r = new ClassPathResource(CARPETA + nombre + ".jpg");
        if (!r.exists()) {
            return Optional.empty();
        }
        try (InputStream in = r.getInputStream()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
