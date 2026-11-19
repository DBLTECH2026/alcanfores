package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * El gateway NUNCA expone {@code /interno/**} (llamadas entre microservicios con {@code X-Interno-Token}). Responde
 * 404 como si la ruta no existiera, también ante trucos de codificación o {@code ..}. Tampoco expone el actuator de
 * otros servicios.
 */
@Component
public class FiltroRutasInternas implements WebFilter, Ordered {

    private final RespuestaError error;

    public FiltroRutasInternas(RespuestaError error) {
        this.error = error;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String original = exchange.getRequest().getPath().value();
        boolean esInterna = RutaSolicitud.empiezaCon(RutaSolicitud.normalizar(original), "/interno");
        if (esInterna || contieneSalto(original)) {
            return error.escribir(exchange, HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "La ruta solicitada no existe.");
        }
        return chain.filter(exchange);
    }

    /** Segmentos {@code ..} (planos o codificados): no tienen uso legítimo en esta API. */
    private static boolean contieneSalto(String ruta) {
        String decodificada = ruta.toLowerCase().replace("%2e", ".").replace("%252e", ".").replaceAll(";[^/]*", "");
        for (String segmento : decodificada.split("/|%2f|%5c|\\\\")) {
            if (segmento.equals("..")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.RUTAS_INTERNAS;
    }
}
