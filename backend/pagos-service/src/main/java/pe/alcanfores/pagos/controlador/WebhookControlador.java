package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.pagos.servicio.WebhookServicio;
import pe.alcanfores.pagos.servicio.WebhookServicio.RespuestaWebhook;

@Tag(name = "Webhooks", description = "Avisos firmados de los proveedores de pago (idempotentes)")
@RestController
@RequestMapping("/api/webhooks")
public class WebhookControlador {

    private final WebhookServicio webhooks;

    public WebhookControlador(WebhookServicio webhooks) {
        this.webhooks = webhooks;
    }

    @Operation(summary = "Aviso de Alcanfores Pay (beta)", description = "Cuerpo firmado con HMAC-SHA256 (X-Firma, secreto PASARELA_BETA_SECRETO) e id en X-Evento-Id. 401 si la firma no es válida.")
    @PostMapping("/pasarela-beta")
    public RespuestaWebhook pasarelaBeta(@RequestBody(required = false) String cuerpo,
                                         @RequestHeader(value = "X-Firma", required = false) String firma,
                                         @RequestHeader(value = "X-Evento-Id", required = false) String idEvento) {
        return webhooks.recibirPasarelaBeta(cuerpo, firma, idEvento);
    }

    @Operation(summary = "Aviso de Mercado Pago", description = "Verifica x-signature (si hay MP_WEBHOOK_SECRETO), consulta GET /v1/payments/{id} y aprueba el intento si está approved.")
    @PostMapping("/mercadopago")
    public RespuestaWebhook mercadoPago(@RequestBody(required = false) String cuerpo,
                                        @RequestParam(name = "data.id", required = false) String dataId,
                                        @RequestParam(name = "type", required = false) String tipo,
                                        @RequestHeader(value = "x-signature", required = false) String firma,
                                        @RequestHeader(value = "x-request-id", required = false) String idSolicitud) {
        return webhooks.recibirMercadoPago(cuerpo, dataId, tipo, firma, idSolicitud);
    }
}
