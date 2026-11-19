package pe.alcanfores.pagos.pasarela.simulador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.config.PagosPropiedades;

import java.time.Duration;

/**
 * Lado PROVEEDOR: avisa al comercio por HTTP ({@code POST /api/webhooks/pasarela-beta}) con el cuerpo firmado con
 * HMAC-SHA256 en {@code X-Firma} y el id del aviso en {@code X-Evento-Id}. Si el comercio no responde 2xx, reintenta
 * con espera creciente (máximo 10 intentos), como hacen las pasarelas reales.
 */
@Component
public class NotificadorComercio {

    private static final Logger log = LoggerFactory.getLogger(NotificadorComercio.class);
    static final int MAX_INTENTOS = 10;

    private final TransaccionPasarelaRepositorio transacciones;
    private final PagosPropiedades propiedades;
    private final Environment entorno;
    private final RestClient http;

    public NotificadorComercio(TransaccionPasarelaRepositorio transacciones, PagosPropiedades propiedades,
                               Environment entorno) {
        this.transacciones = transacciones;
        this.propiedades = propiedades;
        this.entorno = entorno;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    /** Intenta entregar el aviso ahora; si falla queda programado el reintento. */
    public boolean entregar(TransaccionPasarela t) {
        t.setIntentosNotificacion(t.getIntentosNotificacion() + 1);
        try {
            http.post().uri(urlWebhook())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Firma", FirmaHmac.firmar(propiedades.getPasarela().getSecreto(), t.getCuerpoNotificacion()))
                    .header("X-Evento-Id", t.getIdEvento())
                    .body(t.getCuerpoNotificacion())
                    .retrieve()
                    .toBodilessEntity();
            t.setNotificada(true);
            t.setUltimoError(null);
            t.setProximoIntento(null);
        } catch (RestClientException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            t.setUltimoError(error.length() > 255 ? error.substring(0, 255) : error);
            long espera = Math.min(3600, 15L << Math.min(t.getIntentosNotificacion(), 8));
            t.setProximoIntento(Tiempo.ahora().plusSeconds(espera));
            log.warn("La pasarela beta no pudo avisar al comercio ({} intento {}): {}", t.getReferencia(),
                    t.getIntentosNotificacion(), t.getUltimoError());
        }
        transacciones.save(t);
        return t.isNotificada();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reintentarPendientes() {
        transacciones.findTop20ByNotificadaFalseAndIdEventoIsNotNullAndIntentosNotificacionLessThanAndProximoIntentoBeforeOrderByIdAsc(
                MAX_INTENTOS, Tiempo.ahora()).forEach(this::entregar);
    }

    String urlWebhook() {
        String configurada = propiedades.getPasarela().getUrlWebhook();
        if (configurada != null && !configurada.isBlank()) {
            return configurada;
        }
        String puerto = entorno.getProperty("local.server.port", entorno.getProperty("server.port", "8094"));
        return "http://localhost:" + puerto + "/api/webhooks/pasarela-beta";
    }
}
