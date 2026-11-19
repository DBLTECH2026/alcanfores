package pe.alcanfores.notificaciones.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cliente de <b>Evolution API v2</b> (WhatsApp con Baileys). Autenticación con la cabecera {@code apikey}.
 * <ul>
 *   <li>{@code POST /message/sendText/{instancia}} {@code {number, text}}</li>
 *   <li>{@code POST /message/sendMedia/{instancia}} {@code {number, mediatype:"document", mimetype, media(base64), fileName, caption}}</li>
 *   <li>{@code POST /instance/create}, {@code GET /instance/connect/{i}}, {@code GET /instance/connectionState/{i}},
 *       {@code GET /instance/fetchInstances}, {@code DELETE /instance/logout/{i}}</li>
 * </ul>
 * El servicio solo ENVÍA mensajes: no se registran webhooks ni se leen chats.
 */
public class EvolutionCliente {

    private static final Logger log = LoggerFactory.getLogger(EvolutionCliente.class);
    public static final String CABECERA_CLAVE = "apikey";
    public static final String MIME_PDF = "application/pdf";

    private final RestClient rest;
    private final NotificacionesPropiedades.Evolution cfg;

    public EvolutionCliente(RestClient.Builder builder, NotificacionesPropiedades propiedades) {
        this.cfg = propiedades.getEvolution();
        if (cfg.configurado()) {
            builder.baseUrl(sinBarraFinal(cfg.getUrl())).defaultHeader(CABECERA_CLAVE, cfg.getApikey());
        }
        this.rest = builder.build();
    }

    public boolean configurado() {
        return cfg.configurado();
    }

    public String instancia() {
        return cfg.getInstancia();
    }

    /** @return id del mensaje en WhatsApp (si Evolution lo devuelve). */
    public String enviarTexto(String numero, String texto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("number", numero);
        cuerpo.put("text", texto);
        JsonNode r = llamar("enviar el mensaje a " + numero, () -> rest.post()
                .uri("/message/sendText/{instancia}", cfg.getInstancia())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class));
        return idMensaje(r);
    }

    /** Envía un PDF como documento de WhatsApp (base64 sin prefijo {@code data:}). */
    public String enviarDocumento(String numero, byte[] pdf, String nombreArchivo, String leyenda) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("number", numero);
        cuerpo.put("mediatype", "document");
        cuerpo.put("mimetype", MIME_PDF);
        cuerpo.put("media", Base64.getEncoder().encodeToString(pdf));
        cuerpo.put("fileName", nombreArchivo);
        cuerpo.put("caption", leyenda);
        JsonNode r = llamar("enviar el documento a " + numero, () -> rest.post()
                .uri("/message/sendMedia/{instancia}", cfg.getInstancia())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class));
        return idMensaje(r);
    }

    /** Estado de la conexión: {@code open}, {@code connecting} o {@code close} (la instancia aún no existe → close). */
    public EstadoConexion estado() {
        try {
            JsonNode r = llamar("consultar el estado", () -> rest.get()
                    .uri("/instance/connectionState/{instancia}", cfg.getInstancia())
                    .retrieve()
                    .body(JsonNode.class));
            String estado = r == null ? "close" : r.path("instance").path("state").asText(r.path("state").asText("close"));
            return new EstadoConexion(estado, "open".equals(estado) ? numeroConectado() : null);
        } catch (EvolutionExcepcion e) {
            if (e.getStatus() == 404) {
                return new EstadoConexion("close", null);
            }
            throw e;
        }
    }

    /**
     * Pide el QR para vincular el WhatsApp. Si la instancia no existe, la crea ({@code WHATSAPP-BAILEYS}, {@code qrcode:true}).
     * Con {@code numero} (51XXXXXXXXX) Evolution devuelve además el código de emparejamiento de 8 caracteres.
     */
    public CodigoConexion conectar(String numero) {
        JsonNode r;
        try {
            r = llamar("pedir el código QR", () -> rest.get()
                    .uri(u -> {
                        u.path("/instance/connect/{instancia}");
                        if (numero != null) {
                            u.queryParam("number", numero);
                        }
                        return u.build(cfg.getInstancia());
                    })
                    .retrieve()
                    .body(JsonNode.class));
        } catch (EvolutionExcepcion e) {
            if (e.getStatus() != 404) {
                throw e;
            }
            log.info("La instancia {} no existe en Evolution: se crea", cfg.getInstancia());
            r = crearInstancia(numero).path("qrcode");
        }
        return leerCodigo(r);
    }

    /** Cierra la sesión de WhatsApp en la instancia (habrá que escanear el QR de nuevo). */
    public void desconectar() {
        try {
            llamar("desconectar", () -> rest.delete()
                    .uri("/instance/logout/{instancia}", cfg.getInstancia())
                    .retrieve()
                    .body(JsonNode.class));
        } catch (EvolutionExcepcion e) {
            // 400/404: la instancia ya estaba desconectada o no existe; el resultado buscado es el mismo.
            if (e.getStatus() != 400 && e.getStatus() != 404) {
                throw e;
            }
        }
    }

    JsonNode crearInstancia(String numero) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("instanceName", cfg.getInstancia());
        cuerpo.put("integration", "WHATSAPP-BAILEYS");
        cuerpo.put("qrcode", true);
        if (numero != null) {
            cuerpo.put("number", numero);
        }
        JsonNode r = llamar("crear la instancia", () -> rest.post()
                .uri("/instance/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class));
        return r == null ? MissingNode.getInstance() : r;
    }

    /** Número vinculado (ownerJid «51914995187@s.whatsapp.net» → «51914995187»). Si falla, no rompe el estado. */
    private String numeroConectado() {
        try {
            JsonNode r = rest.get()
                    .uri(u -> u.path("/instance/fetchInstances").queryParam("instanceName", cfg.getInstancia()).build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode jid = r == null ? null : r.findValue("ownerJid");
            if (jid == null && r != null) {
                jid = r.findValue("owner");
            }
            if (jid == null || jid.isNull() || jid.asText().isBlank()) {
                return null;
            }
            String texto = jid.asText();
            return texto.contains("@") ? texto.substring(0, texto.indexOf('@')) : texto;
        } catch (RestClientException e) {
            log.debug("No se pudo leer el número conectado: {}", e.getMessage());
            return null;
        }
    }

    private static CodigoConexion leerCodigo(JsonNode r) {
        if (r == null || r.isMissingNode()) {
            return new CodigoConexion(null, null, "connecting");
        }
        String estado = r.path("instance").path("state").asText(null);
        String qr = textoONulo(r.path("base64"));
        String codigo = textoONulo(r.path("pairingCode"));
        if (qr == null && "open".equals(estado)) {
            return new CodigoConexion(null, null, "open");
        }
        return new CodigoConexion(qr, codigo, "connecting");
    }

    private static String textoONulo(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }

    private static String idMensaje(JsonNode r) {
        return r == null ? null : textoONulo(r.path("key").path("id"));
    }

    private JsonNode llamar(String accion, Supplier<JsonNode> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            throw new EvolutionExcepcion(e.getStatusCode().value(), traducir(accion, e));
        } catch (RestClientException e) {
            throw new EvolutionExcepcion("No se pudo conectar con Evolution API para " + accion + ": " + e.getMessage(), e);
        }
    }

    private String traducir(String accion, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String cuerpo = e.getResponseBodyAsString();
        if (status == 401 || status == 403) {
            return "Evolution API rechazó la clave (EVOLUTION_APIKEY) al " + accion + ".";
        }
        if (status == 404) {
            return "La instancia «" + cfg.getInstancia() + "» no existe en Evolution API. Conéctala desde el panel.";
        }
        if (status == 400 && cuerpo != null && cuerpo.contains("\"exists\":false")) {
            return "El número no tiene WhatsApp; no se pudo " + accion + ".";
        }
        String detalle = cuerpo == null ? "" : cuerpo.substring(0, Math.min(cuerpo.length(), 300));
        return "Evolution API respondió " + status + " al " + accion + (detalle.isBlank() ? "." : ": " + detalle);
    }

    private static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Estado de la conexión y número vinculado (solo si está {@code open}). */
    public record EstadoConexion(String estado, String numero) {
    }

    /** QR (data URL base64 tal como lo da Evolution) y código de emparejamiento opcional. */
    public record CodigoConexion(String qrBase64, String codigoEmparejamiento, String estado) {
    }
}
