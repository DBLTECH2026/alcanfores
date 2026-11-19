package pe.alcanfores.comun.seguridad;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Para los clientes HTTP entre microservicios ({@code RestClient}): agrega {@code X-Interno-Token} y propaga el
 * {@code X-Request-Id} de la solicitud original (trazabilidad de punta a punta).
 * <pre>{@code
 * @Bean @LoadBalanced RestClient.Builder clienteBalanceado(InterceptorInterno i) {
 *     return RestClient.builder().requestInterceptor(i);
 * }
 * }</pre>
 */
public class InterceptorInterno implements ClientHttpRequestInterceptor {

    private final String token;

    public InterceptorInterno(String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest req, byte[] cuerpo, ClientHttpRequestExecution ejecucion) throws IOException {
        req.getHeaders().set(InternoFiltro.CABECERA, token);
        String idSolicitud = MDC.get(FiltroIdSolicitud.MDC_CLAVE);
        if (idSolicitud != null) {
            req.getHeaders().set(FiltroIdSolicitud.CABECERA, idSolicitud);
        }
        return ejecucion.execute(req, cuerpo);
    }
}
