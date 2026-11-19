package pe.alcanfores.pagos.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.config.PagosPropiedades;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cliente REST de Mercado Pago con el access token del comercio (Bearer). Timeout 10 s. */
@Component
public class MercadoPagoClienteHttp implements MercadoPagoCliente {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClienteHttp.class);
    static final String MENSAJE_CAIDO = "Mercado Pago no responde en este momento. Intenta en unos minutos o elige otro método.";

    private final RestClient rest;

    @Autowired
    public MercadoPagoClienteHttp(PagosPropiedades propiedades) {
        this(RestClient.builder().requestFactory(fabrica()), propiedades);
    }

    /** Para pruebas con {@code MockRestServiceServer}. */
    public MercadoPagoClienteHttp(RestClient.Builder builder, PagosPropiedades propiedades) {
        PagosPropiedades.MercadoPago mp = propiedades.getMercadopago();
        this.rest = builder.baseUrl(mp.getUrlApi())
                .defaultHeaders(h -> {
                    if (mp.configurado()) {
                        h.setBearerAuth(mp.getAccessToken());
                    }
                })
                .build();
    }

    private static SimpleClientHttpRequestFactory fabrica() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(10));
        return f;
    }

    @Override
    public PreferenciaMp crearPreferencia(Map<String, Object> cuerpo, String claveIdempotencia) {
        try {
            JsonNode r = rest.post().uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", claveIdempotencia)
                    .body(cuerpo).retrieve().body(JsonNode.class);
            if (r == null || !r.hasNonNull("id")) {
                throw new RestClientException("Respuesta sin id de preferencia");
            }
            return new PreferenciaMp(r.get("id").asText(), texto(r, "init_point"), texto(r, "sandbox_init_point"));
        } catch (RestClientException e) {
            log.warn("No se pudo crear la preferencia de Mercado Pago: {}", e.getMessage());
            throw NegocioExcepcion.noDisponible("MP_NO_DISPONIBLE", MENSAJE_CAIDO);
        }
    }

    @Override
    public Optional<PagoMp> obtenerPago(String idPago) {
        try {
            return Optional.ofNullable(rest.get().uri("/v1/payments/{id}", idPago).retrieve().body(JsonNode.class))
                    .map(MercadoPagoClienteHttp::pago);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("No se pudo consultar el pago {} en Mercado Pago: {}", idPago, e.getMessage());
            throw NegocioExcepcion.noDisponible("MP_NO_DISPONIBLE", MENSAJE_CAIDO);
        }
    }

    @Override
    public List<PagoMp> buscarPorReferencia(String referenciaExterna) {
        try {
            JsonNode r = rest.get().uri(u -> u.path("/v1/payments/search")
                            .queryParam("external_reference", referenciaExterna)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc").build())
                    .retrieve().body(JsonNode.class);
            List<PagoMp> pagos = new ArrayList<>();
            if (r != null) {
                r.path("results").forEach(n -> pagos.add(pago(n)));
            }
            return pagos;
        } catch (RestClientException e) {
            log.warn("No se pudo buscar en Mercado Pago la referencia {}: {}", referenciaExterna, e.getMessage());
            throw NegocioExcepcion.noDisponible("MP_NO_DISPONIBLE", MENSAJE_CAIDO);
        }
    }

    static PagoMp pago(JsonNode n) {
        BigDecimal monto = n.hasNonNull("transaction_amount") ? n.get("transaction_amount").decimalValue() : null;
        return new PagoMp(texto(n, "id"), texto(n, "status"), texto(n, "status_detail"), texto(n, "external_reference"),
                monto, texto(n, "payment_method_id"), texto(n, "payment_type_id"), texto(n, "authorization_code"),
                texto(n.path("card"), "last_four_digits"));
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
