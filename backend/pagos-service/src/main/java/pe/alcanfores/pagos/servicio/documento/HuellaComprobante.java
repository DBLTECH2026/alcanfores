package pe.alcanfores.pagos.servicio.documento;

import pe.alcanfores.pagos.modelo.Comprobante;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Hash del comprobante (SHA-256 del XML UBL, en base64) y cadena del QR con el formato de SUNAT. */
public final class HuellaComprobante {

    private HuellaComprobante() {
    }

    public static String sha256Base64(String xml) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** {@code RUC|tipo|serie|numero|igv|total|fecha|tipoDocAdq|numDocAdq|hash} (hash = valor resumen de 28 caracteres). */
    public static String cadenaQr(Comprobante c) {
        return String.join("|", c.getEmisorRuc(), c.getCodigoTipo(), c.getSerie(), String.format("%08d", c.getCorrelativo()),
                c.getIgv().toPlainString(), c.getTotal().toPlainString(), c.getFechaEmision().toLocalDate().toString(),
                c.getClienteTipoDoc(), c.getClienteNumDoc(), c.valorResumen());
    }
}
