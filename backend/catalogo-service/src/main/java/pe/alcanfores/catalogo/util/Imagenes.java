package pe.alcanfores.catalogo.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Reconoce imágenes por sus bytes mágicos (nunca por la extensión ni por el Content-Type que manda el navegador)
 * y lee sus dimensiones sin decodificar toda la imagen.
 */
public final class Imagenes {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public enum Tipo {
        JPG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String mime;

        Tipo(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }

        public String extension() {
            return extension;
        }

        public String mime() {
            return mime;
        }

        public static Optional<Tipo> porExtension(String archivo) {
            String ext = archivo.substring(archivo.lastIndexOf('.') + 1);
            for (Tipo t : values()) {
                if (t.extension.equalsIgnoreCase(ext) || (t == JPG && "jpeg".equalsIgnoreCase(ext))) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }
    }

    public record Dimensiones(int ancho, int alto) {
    }

    private Imagenes() {
    }

    /** JPG: FF D8 FF · PNG: 89 50 4E 47 0D 0A 1A 0A · WebP: «RIFF» + 4 bytes de tamaño + «WEBP». */
    public static Optional<Tipo> detectar(byte[] b) {
        if (b == null) {
            return Optional.empty();
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(Tipo.JPG);
        }
        if (empiezaCon(b, FIRMA_PNG, 0)) {
            return Optional.of(Tipo.PNG);
        }
        if (empiezaCon(b, new byte[]{'R', 'I', 'F', 'F'}, 0) && empiezaCon(b, new byte[]{'W', 'E', 'B', 'P'}, 8)) {
            return Optional.of(Tipo.WEBP);
        }
        return Optional.empty();
    }

    /** Ancho y alto leídos de la cabecera; vacío si Java no tiene lector para el formato (WebP) o el archivo está dañado. */
    public static Optional<Dimensiones> dimensiones(byte[] b) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(b))) {
            if (entrada == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                return Optional.empty();
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                return Optional.of(new Dimensiones(lector.getWidth(0), lector.getHeight(0)));
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean empiezaCon(byte[] b, byte[] firma, int desde) {
        if (b.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (b[desde + i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
