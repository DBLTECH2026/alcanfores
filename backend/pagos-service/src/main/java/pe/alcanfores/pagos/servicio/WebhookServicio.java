package pe.alcanfores.pagos.servicio;

/** Avisos (webhooks) de los proveedores de pago: firma, idempotencia y aprobación del intento. */
public interface WebhookServicio {

    /** Pasarela beta: cabeceras {@code X-Firma} (HMAC-SHA256 hex del cuerpo) y {@code X-Evento-Id}. */
    RespuestaWebhook recibirPasarelaBeta(String cuerpo, String firma, String idEvento);

    /** Mercado Pago: {@code x-signature} ({@code ts=…,v1=…}), {@code x-request-id} y {@code data.id}. */
    RespuestaWebhook recibirMercadoPago(String cuerpo, String dataIdConsulta, String tipoConsulta, String firma,
                                        String idSolicitud);

    record RespuestaWebhook(boolean recibido, boolean duplicado, String resultado) {
    }
}
