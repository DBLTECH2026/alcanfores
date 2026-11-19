package pe.alcanfores.gateway.filtro;

import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Clave con la que el limitador cuenta las solicitudes: la IP del cliente, salvo cuando llama Canchita (asistente con
 * IA) por la red interna. Canchita sale siempre desde la misma IP; si contara por IP, todas sus conversaciones
 * compartirían un solo cupo (p. ej. 10 reservas por minuto para todos los clientes de WhatsApp). Con un
 * {@code X-Interno-Token} VÁLIDO y {@code X-Canchita-Conversacion}, cada conversación tiene su propio cupo. Sin token
 * válido la cabecera de conversación se ignora (nadie de afuera puede saltarse el límite inventándola); el gateway
 * igual borra el token antes de reenviar la solicitud a los servicios.
 */
public final class ClaveLimite {

    public static final String CABECERA_CONVERSACION = "X-Canchita-Conversacion";
    private static final Pattern CONVERSACION = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");

    private ClaveLimite() {
    }

    public static String de(ServerWebExchange exchange, String internoToken) {
        String token = exchange.getRequest().getHeaders().getFirst("X-Interno-Token");
        String conversacion = exchange.getRequest().getHeaders().getFirst(CABECERA_CONVERSACION);
        if (conversacion != null && CONVERSACION.matcher(conversacion).matches() && tokenValido(token, internoToken)) {
            return "canchita:" + conversacion;
        }
        return IpCliente.de(exchange);
    }

    static boolean tokenValido(String recibido, String esperado) {
        if (recibido == null || esperado == null || esperado.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(recibido.getBytes(StandardCharsets.UTF_8), esperado.getBytes(StandardCharsets.UTF_8));
    }
}
