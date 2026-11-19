package pe.alcanfores.notificaciones.cliente;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.DatosReserva;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Cliente de reservas-service (rutas {@code /interno/**}, balanceado por Eureka). */
@Component
public class ReservasCliente {

    static final String BASE = "http://reservas-service/interno/reservas/";
    private static final String SERVICIO = "reservas-service";
    private static final String NOMBRE = "reservas";

    private final RestClient consultas;
    private final RestClient documentos;
    private final LlamadaProtegida protegida;
    private final ObjectMapper json;

    public ReservasCliente(@Qualifier("restInterno") RestClient consultas, @Qualifier("restDocumentos") RestClient documentos,
                           LlamadaProtegida protegida, ObjectMapper json) {
        this.consultas = consultas;
        this.documentos = documentos;
        this.protegida = protegida;
        this.json = json;
    }

    /** Constancia de reserva en PDF ({@code GET /interno/reservas/{codigo}/constancia.pdf}). */
    public byte[] constanciaPdf(String codigoReserva) {
        return protegida.ejecutar(SERVICIO, NOMBRE, () -> documentos.get()
                .uri(BASE + "{codigo}/constancia.pdf", codigoReserva)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class));
    }

    /**
     * Datos vigentes de la reserva: contacto del cliente (los eventos de pagos no lo traen), estado y saldo.
     * Acepta la respuesta plana (DatosReserva + estado…) o anidada ({@code {reserva: DatosReserva, estado…}}).
     */
    public ReservaInterna consultar(String codigoReserva) {
        JsonNode nodo = protegida.ejecutar(SERVICIO, NOMBRE, () -> consultas.get()
                .uri(BASE + "{codigo}", codigoReserva)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
            throw NegocioExcepcion.noEncontrado("No encontramos la reserva " + codigoReserva + ".");
        }
        JsonNode datos = nodo.hasNonNull("reserva") ? nodo.get("reserva") : nodo;
        try {
            DatosReserva reserva = json.readerFor(DatosReserva.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(datos);
            return new ReservaInterna(reserva, texto(nodo, "estado"),
                    texto(nodo, "estadoPago"), decimal(nodo, "saldo"), fechaHora(nodo, "venceEn"));
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            throw NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE",
                    "El servicio de reservas devolvió datos que no se pudieron leer.");
        }
    }

    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }

    private static BigDecimal decimal(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).decimalValue() : null;
    }

    private static LocalDateTime fechaHora(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? LocalDateTime.parse(n.get(campo).asText()) : null;
    }
}
