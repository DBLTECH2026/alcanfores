package pe.alcanfores.gateway.filtro;

import org.springframework.core.Ordered;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

/**
 * CORS global con el orden fijado en {@link OrdenFiltros}: responde los preflight sin pasar por los demás filtros y
 * agrega las cabeceras CORS también a las respuestas de error del propio gateway.
 */
public class FiltroCors extends CorsWebFilter implements Ordered {

    public FiltroCors(CorsConfigurationSource origen) {
        super(origen);
    }

    @Override
    public int getOrder() {
        return OrdenFiltros.CORS;
    }
}
