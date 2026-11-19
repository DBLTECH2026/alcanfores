package pe.alcanfores.pagos.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades propias del servicio ({@code alcanfores.pagos.*}); los secretos llegan por variables de entorno. */
@Getter
@Setter
@ConfigurationProperties(prefix = "alcanfores.pagos")
public class PagosPropiedades {

    /** Carpeta raíz de archivos subidos; los comprobantes van en {@code <uploadsDir>/comprobantes}. */
    private String uploadsDir = "./uploads";
    private final Pasarela pasarela = new Pasarela();
    private final MercadoPago mercadopago = new MercadoPago();

    @Getter
    @Setter
    public static class Pasarela {
        /** PASARELA_BETA_SECRETO: firma HMAC-SHA256 de los webhooks de la pasarela beta. */
        private String secreto = "secreto-pasarela-beta-solo-para-desarrollo-local";
        /** URL a la que la pasarela avisa; vacía = este mismo servicio en localhost. */
        private String urlWebhook = "";
        private int minutosExpiracion = 15;
    }

    @Getter
    @Setter
    public static class MercadoPago {
        /** MP_ACCESS_TOKEN (TEST-… en sandbox). Vacío = Mercado Pago desactivado. */
        private String accessToken = "";
        /** MP_WEBHOOK_SECRETO: si existe se verifica la cabecera x-signature. */
        private String webhookSecreto = "";
        private String urlApi = "https://api.mercadopago.com";

        public boolean configurado() {
            return accessToken != null && !accessToken.isBlank();
        }

        public boolean sandbox() {
            return configurado() && accessToken.startsWith("TEST-");
        }
    }
}
