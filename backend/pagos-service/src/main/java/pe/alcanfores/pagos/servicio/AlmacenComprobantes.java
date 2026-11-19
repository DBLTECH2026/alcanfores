package pe.alcanfores.pagos.servicio;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.config.PagosPropiedades;
import pe.alcanfores.pagos.dto.Mensajes;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Imágenes de comprobantes Yape/Plin en {@code <uploads>/comprobantes}. Se aceptan solo JPG o PNG reconocidos por sus
 * BYTES MÁGICOS (no por la extensión), de hasta 5 MB y al menos 200 px por lado; se guardan con nombre UUID.
 */
@Component
public class AlmacenComprobantes {

    public static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    public static final int LADO_MINIMO = 200;
    private static final Pattern NOMBRE_SEGURO = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final Path carpeta;

    public AlmacenComprobantes(PagosPropiedades propiedades) {
        this.carpeta = Path.of(propiedades.getUploadsDir(), "comprobantes").toAbsolutePath().normalize();
    }

    /** Formato reconocido por la cabecera del archivo. */
    public enum Formato {
        JPG("jpg", MediaType.IMAGE_JPEG_VALUE), PNG("png", MediaType.IMAGE_PNG_VALUE);

        final String extension;
        final String tipoContenido;

        Formato(String extension, String tipoContenido) {
            this.extension = extension;
            this.tipoContenido = tipoContenido;
        }
    }

    /**
     * Valida la imagen y devuelve el mensaje de error para el campo {@code archivo}, o vacío si es válida.
     */
    public Optional<String> problema(byte[] contenido) {
        if (contenido == null || contenido.length == 0 || contenido.length > MAXIMO_BYTES) {
            return Optional.of(Mensajes.IMAGEN);
        }
        Optional<Formato> formato = formato(contenido);
        if (formato.isEmpty()) {
            return Optional.of(Mensajes.IMAGEN);
        }
        int[] dimensiones = dimensiones(contenido);
        if (dimensiones == null) {
            return Optional.of(Mensajes.IMAGEN);
        }
        if (dimensiones[0] < LADO_MINIMO || dimensiones[1] < LADO_MINIMO) {
            return Optional.of(Mensajes.IMAGEN_PEQUENA);
        }
        return Optional.empty();
    }

    public static Optional<Formato> formato(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(Formato.JPG);
        }
        if (b.length >= FIRMA_PNG.length) {
            for (int i = 0; i < FIRMA_PNG.length; i++) {
                if (b[i] != FIRMA_PNG[i]) {
                    return Optional.empty();
                }
            }
            return Optional.of(Formato.PNG);
        }
        return Optional.empty();
    }

    /** Ancho y alto leyendo solo la cabecera (no decodifica la imagen completa). */
    static int[] dimensiones(byte[] contenido) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in);
                return new int[]{lector.getWidth(0), lector.getHeight(0)};
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Guarda una imagen ya validada y devuelve su nombre (UUID + extensión real). */
    public String guardar(byte[] contenido) {
        Formato formato = formato(contenido).orElseThrow(() -> NegocioExcepcion.validacion("archivo", Mensajes.IMAGEN));
        String nombre = UUID.randomUUID() + "." + formato.extension;
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve(nombre), contenido);
            return nombre;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el comprobante", e);
        }
    }

    public void borrar(String nombre) {
        if (nombre != null && NOMBRE_SEGURO.matcher(nombre).matches()) {
            try {
                Files.deleteIfExists(carpeta.resolve(nombre));
            } catch (IOException e) {
                // Si no se puede borrar queda como archivo huérfano; no afecta al negocio.
            }
        }
    }

    /** Lee una imagen guardada; solo nombres UUID generados por el sistema (nunca rutas del usuario). */
    public Imagen leer(String nombre) {
        if (nombre == null || !NOMBRE_SEGURO.matcher(nombre).matches()) {
            throw NegocioExcepcion.noEncontrado("Este pago no tiene imagen de comprobante.");
        }
        Path ruta = carpeta.resolve(nombre).normalize();
        if (!ruta.startsWith(carpeta) || !Files.isRegularFile(ruta)) {
            throw NegocioExcepcion.noEncontrado("No encontramos la imagen del comprobante.");
        }
        try {
            byte[] bytes = Files.readAllBytes(ruta);
            String tipo = formato(bytes).map(f -> f.tipoContenido).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return new Imagen(nombre, tipo, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Imagen(String nombre, String tipoContenido, byte[] contenido) {
    }
}
