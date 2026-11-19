package pe.alcanfores.catalogo;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Imágenes generadas en memoria para las pruebas de subida. */
public final class ImagenesDePrueba {

    private ImagenesDePrueba() {
    }

    public static byte[] png(int ancho, int alto) {
        return imagen(ancho, alto, "png");
    }

    public static byte[] jpg(int ancho, int alto) {
        return imagen(ancho, alto, "jpg");
    }

    /** Cabecera RIFF/WEBP válida (Java no trae lector WebP: basta con la firma). */
    public static byte[] webp() {
        byte[] b = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        b[4] = 56;
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 8);
        return b;
    }

    private static byte[] imagen(int ancho, int alto, String formato) {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x27811F));
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(img, formato, salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
