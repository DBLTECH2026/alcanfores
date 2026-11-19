package pe.alcanfores.pagos.controlador;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.pagos.dto.DatosComprobanteSolicitud;
import pe.alcanfores.pagos.dto.EstadoIntentoRespuesta;
import pe.alcanfores.pagos.dto.FormularioComprobante;
import pe.alcanfores.pagos.dto.IntentoCreadoRespuesta;
import pe.alcanfores.pagos.dto.Mensajes;
import pe.alcanfores.pagos.dto.MetodosPagoRespuesta;
import pe.alcanfores.pagos.dto.PagoClienteRespuesta;
import pe.alcanfores.pagos.dto.PagoRegistradoRespuesta;
import pe.alcanfores.pagos.dto.PreferenciaRespuesta;
import pe.alcanfores.pagos.dto.SolicitudIntento;
import pe.alcanfores.pagos.servicio.IntentoPagoServicio;
import pe.alcanfores.pagos.servicio.PagoServicio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Tag(name = "Pagos (cliente)", description = "Medios de pago, comprobante Yape/Plin y pagos en línea")
@RestController
@RequestMapping("/api/pagos")
public class PagoPublicoControlador {

    private final PagoServicio pagos;
    private final IntentoPagoServicio intentos;
    private final ObjectMapper json;

    public PagoPublicoControlador(PagoServicio pagos, IntentoPagoServicio intentos, ObjectMapper json) {
        this.pagos = pagos;
        this.intentos = intentos;
        this.json = json;
    }

    @Operation(summary = "Medios de pago disponibles", description = "Yape/Plin del complejo, pasarela beta, Mercado Pago (si está configurado) y datos de prueba.")
    @GetMapping("/metodos")
    public MetodosPagoRespuesta metodos() {
        return pagos.metodos();
    }

    @Operation(summary = "Subir comprobante de Yape o Plin",
            description = "Multipart: codigoReserva, dni, metodo (YAPE|PLIN), codigoOperacion, archivo (JPG/PNG ≤ 5 MB, ≥ 200 px) "
                    + "y comprobante (JSON {tipo, ruc?, razonSocial?, direccionFiscal?}). Crea un pago PENDIENTE por el adelanto.")
    @PostMapping(value = "/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PagoRegistradoRespuesta subirComprobante(@RequestParam(required = false) String codigoReserva,
                                                    @RequestParam(required = false) String dni,
                                                    @RequestParam(required = false) String metodo,
                                                    @RequestParam(required = false) String codigoOperacion,
                                                    @RequestParam(required = false) MultipartFile archivo,
                                                    @RequestParam(required = false) String comprobante) {
        FormularioComprobante formulario = new FormularioComprobante(recortar(codigoReserva), recortar(dni),
                recortar(metodo), recortar(codigoOperacion), leerComprobante(comprobante));
        return pagos.registrarComprobante(formulario, bytes(archivo));
    }

    @Operation(summary = "Pagos de una reserva", description = "Para «Mi reserva»: pagos con su estado y enlaces a la boleta o factura.")
    @GetMapping("/reserva/{codigo}")
    public List<PagoClienteRespuesta> pagosDeReserva(@PathVariable @CodigoReserva String codigo,
                                                     @RequestParam(required = false) @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return pagos.pagosDeReserva(codigo, dni);
    }

    @Operation(summary = "Crear pago con Alcanfores Pay (beta)", description = "Devuelve la referencia ALP-… y la URL del checkout (expira en 15 min).")
    @PostMapping("/pasarela/intentos")
    @ResponseStatus(HttpStatus.CREATED)
    public IntentoCreadoRespuesta crearIntento(@Valid @RequestBody SolicitudIntento solicitud) {
        return intentos.crearIntentoBeta(solicitud);
    }

    @Operation(summary = "Estado de un pago en línea", description = "Sondeo de la página de resultado; en Mercado Pago consulta el pago si sigue pendiente («Ya pagué, verificar»).")
    @GetMapping("/intentos/{referencia}/estado")
    public EstadoIntentoRespuesta estado(@PathVariable String referencia) {
        return intentos.estado(referencia);
    }

    @Operation(summary = "Crear preferencia de Mercado Pago (sandbox)", description = "409 MP_NO_CONFIGURADO si falta MP_ACCESS_TOKEN.")
    @PostMapping("/mercadopago/preferencias")
    @ResponseStatus(HttpStatus.CREATED)
    public PreferenciaRespuesta crearPreferencia(@Valid @RequestBody SolicitudIntento solicitud) {
        return intentos.crearPreferenciaMercadoPago(solicitud);
    }

    private DatosComprobanteSolicitud leerComprobante(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return json.readValue(texto, DatosComprobanteSolicitud.class);
        } catch (JsonProcessingException e) {
            throw NegocioExcepcion.validacion("comprobante", "Revisa los datos del comprobante (boleta o factura).");
        }
    }

    private static byte[] bytes(MultipartFile archivo) {
        try {
            return archivo == null || archivo.isEmpty() ? null : archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String recortar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
