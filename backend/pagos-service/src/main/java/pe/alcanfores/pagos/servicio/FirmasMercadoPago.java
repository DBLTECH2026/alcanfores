package pe.alcanfores.pagos.servicio;

import pe.alcanfores.pagos.pasarela.simulador.FirmaHmac;

import java.util.Locale;

/** Verificación de la cabecera {@code x-signature} de los webhooks de Mercado Pago. */
public final class FirmasMercadoPago {

    private FirmasMercadoPago() {
    }

    /**
     * Manifest de Mercado Pago: {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} firmado con HMAC-SHA256;
     * se compara con {@code v1} de la cabecera {@code x-signature: ts=…,v1=…}.
     */
    public static boolean valida(String secreto, String cabecera, String dataId, String idSolicitud) {
        if (cabecera == null || dataId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : cabecera.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("ts")) {
                ts = kv[1].trim();
            } else if (kv.length == 2 && kv[0].trim().equals("v1")) {
                v1 = kv[1].trim();
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase(Locale.ROOT) : dataId;
        StringBuilder manifest = new StringBuilder("id:").append(id).append(';');
        if (idSolicitud != null && !idSolicitud.isBlank()) {
            manifest.append("request-id:").append(idSolicitud).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return FirmaHmac.verificar(secreto, manifest.toString(), v1);
    }
}
