package pe.alcanfores.pagos.pasarela.simulador;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 en hexadecimal (firma de webhooks) y comparación en tiempo constante. */
public final class FirmaHmac {

    private FirmaHmac() {
    }

    public static String firmar(String secreto, String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    public static boolean verificar(String secreto, String contenido, String firmaRecibida) {
        if (secreto == null || secreto.isBlank() || contenido == null || firmaRecibida == null) {
            return false;
        }
        byte[] esperada = firmar(secreto, contenido).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = firmaRecibida.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, recibida);
    }
}
