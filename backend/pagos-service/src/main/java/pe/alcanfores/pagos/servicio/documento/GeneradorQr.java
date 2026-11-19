package pe.alcanfores.pagos.servicio.documento;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Código QR en PNG (como data URI para incrustarlo en el HTML del PDF). */
public final class GeneradorQr {

    private GeneradorQr() {
    }

    public static byte[] png(String texto, int lado) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado,
                    Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String dataUri(String texto, int lado) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(texto, lado));
    }
}
