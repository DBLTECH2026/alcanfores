package pe.alcanfores.notificaciones.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pe.alcanfores.comun.config.AlcanforesPropiedades;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Cifra la contraseña SMTP con AES-256-GCM. La llave es el SHA-256 de {@code CORREO_CLAVE_CIFRADO} o, si no está
 * definida, de {@code JWT_SECRETO}. Formato guardado: base64(iv de 12 bytes + texto cifrado con su etiqueta).
 */
@Component
public class CifradorClaves {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKeySpec llave;
    private final SecureRandom azar = new SecureRandom();

    public CifradorClaves(NotificacionesPropiedades propiedades, AlcanforesPropiedades alcanfores) {
        String base = StringUtils.hasText(propiedades.getCorreo().getClaveCifrado())
                ? propiedades.getCorreo().getClaveCifrado() : alcanfores.getJwt().getSecreto();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            this.llave = new SecretKeySpec(hash, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }

    public String cifrar(String clave) {
        try {
            byte[] iv = new byte[BYTES_IV];
            azar.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGORITMO);
            c.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            byte[] cifrado = c.doFinal(clave.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la contraseña del correo", e);
        }
    }

    /** Vacío si el texto no se puede descifrar (por ejemplo, porque cambió la llave del servidor). */
    public Optional<String> descifrar(String guardado) {
        try {
            byte[] todo = Base64.getDecoder().decode(guardado);
            Cipher c = Cipher.getInstance(ALGORITMO);
            c.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV));
            return Optional.of(new String(c.doFinal(todo, BYTES_IV, todo.length - BYTES_IV), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
