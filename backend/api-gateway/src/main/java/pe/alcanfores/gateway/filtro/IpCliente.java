package pe.alcanfores.gateway.filtro;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * IP del cliente: la primera de {@code X-Forwarded-For} (la escribe el proxy inverso de producción) o la dirección
 * remota de la conexión. Es el mismo criterio que usa {@code comun} para la auditoría.
 */
public final class IpCliente {

    private IpCliente() {
    }

    public static String de(ServerWebExchange exchange) {
        String reenviada = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].trim();
            if (!primera.isEmpty() && primera.length() <= 45) {
                return primera;
            }
        }
        InetSocketAddress remota = exchange.getRequest().getRemoteAddress();
        if (remota == null) {
            return "desconocida";
        }
        return remota.getAddress() != null ? remota.getAddress().getHostAddress() : remota.getHostString();
    }
}
