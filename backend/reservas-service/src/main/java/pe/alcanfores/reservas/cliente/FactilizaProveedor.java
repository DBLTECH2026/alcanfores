package pe.alcanfores.reservas.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.reservas.config.ReservasPropiedades;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;

import java.util.Optional;
import java.util.function.Function;

/**
 * Factiliza ({@code https://api.factiliza.com/v1}): {@code GET /dni/info/{dni}} y {@code GET /ruc/info/{ruc}} con
 * {@code Authorization: Bearer <FACTILIZA_TOKEN>}. Respuesta {@code {success, data:{…}}}. Timeout de 15 s:
 * Factiliza tarda ~11 s en responder 404 cuando el número no existe. 404/400/422 → «no encontrado» (vacío); cualquier
 * otro error o falta de respuesta → 503 {@code CONSULTA_NO_DISPONIBLE}.
 */
public class FactilizaProveedor implements ProveedorDocumentos {

    public static final String FUENTE_DNI = "RENIEC (Factiliza)";
    public static final String FUENTE_RUC = "SUNAT (Factiliza)";
    private static final Logger log = LoggerFactory.getLogger(FactilizaProveedor.class);

    private final boolean configurado;
    private final RestClient http;

    public FactilizaProveedor(RestClient.Builder builder, ReservasPropiedades.Factiliza conf) {
        this.configurado = conf.configurado();
        this.http = builder.baseUrl(conf.url())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (configurado ? conf.token().trim() : ""))
                .build();
    }

    @Override
    public boolean disponible() {
        return configurado;
    }

    @Override
    public Optional<DniRespuesta> consultarDni(String dni) {
        return consultar("/dni/info/{numero}", dni, "RENIEC", d -> {
            String paterno = Texto.titulo(d.path("apellido_paterno").asText(""));
            String materno = Texto.titulo(d.path("apellido_materno").asText(""));
            String nombres = Texto.titulo(d.path("nombres").asText(""));
            if (nombres == null || nombres.isBlank()) {
                return null;
            }
            return new DniRespuesta(dni, nombres, paterno, materno, (paterno + " " + materno).trim(), FUENTE_DNI);
        });
    }

    @Override
    public Optional<RucRespuesta> consultarRuc(String ruc) {
        return consultar("/ruc/info/{numero}", ruc, "SUNAT", d -> {
            String razon = Texto.limpiar(d.path("nombre_o_razon_social").asText(""));
            if (razon == null || razon.isBlank()) {
                return null;
            }
            return new RucRespuesta(ruc, razon, Texto.limpiar(d.path("direccion_completa").asText(d.path("direccion").asText(""))),
                    d.path("estado").asText(null), d.path("condicion").asText(null), FUENTE_RUC);
        });
    }

    private <T> Optional<T> consultar(String ruta, String numero, String entidad, Function<JsonNode, T> mapeo) {
        if (!configurado) {
            throw noDisponible(entidad);
        }
        try {
            JsonNode cuerpo = http.get().uri(ruta, numero).retrieve().body(JsonNode.class);
            if (cuerpo == null || !cuerpo.path("success").asBoolean(false) || !cuerpo.path("data").isObject()) {
                return Optional.empty();
            }
            return Optional.ofNullable(mapeo.apply(cuerpo.get("data")));
        } catch (RestClientResponseException e) {
            HttpStatus estado = HttpStatus.resolve(e.getStatusCode().value());
            if (estado == HttpStatus.NOT_FOUND || estado == HttpStatus.BAD_REQUEST || estado == HttpStatus.UNPROCESSABLE_ENTITY) {
                return Optional.empty();
            }
            log.warn("Factiliza respondió {} al consultar {}", e.getStatusCode().value(), entidad);
            throw noDisponible(entidad);
        } catch (RestClientException e) {
            log.warn("Factiliza no responde al consultar {}: {}", entidad, e.getMessage());
            throw noDisponible(entidad);
        }
    }

    private static NegocioExcepcion noDisponible(String entidad) {
        return NegocioExcepcion.noDisponible("CONSULTA_NO_DISPONIBLE",
                "La consulta a " + entidad + " no está disponible en este momento. Escribe los datos a mano.");
    }
}
