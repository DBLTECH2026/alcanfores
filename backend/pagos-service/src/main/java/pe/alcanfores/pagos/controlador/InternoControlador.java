package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.pagos.dto.CobroRespuesta;
import pe.alcanfores.pagos.dto.SolicitudCajaInterna;
import pe.alcanfores.pagos.dto.SolicitudValidacionIa;
import pe.alcanfores.pagos.servicio.ComprobanteServicio;
import pe.alcanfores.pagos.servicio.PagoServicio;

/** Rutas entre microservicios (cabecera X-Interno-Token); el gateway nunca las expone. */
@Tag(name = "Interno", description = "Solo para otros microservicios")
@RestController
@RequestMapping("/interno")
public class InternoControlador {

    private final PagoServicio pagos;
    private final ComprobanteServicio comprobantes;

    public InternoControlador(PagoServicio pagos, ComprobanteServicio comprobantes) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
    }

    @Operation(summary = "Cobro en caja para una reserva manual", description = "Lo usa reservas-service; puede traer la reserva (DatosReserva) para no consultarla de vuelta.")
    @PostMapping("/pagos/caja")
    @ResponseStatus(HttpStatus.CREATED)
    public CobroRespuesta caja(@Valid @RequestBody SolicitudCajaInterna solicitud) {
        return pagos.cobrarEnCajaInterno(solicitud);
    }

    @Operation(summary = "Validación automática de Canchita (IA)",
            description = "Lo usa asistente-service cuando la foto del comprobante calza con la reserva: misma validación del panel "
                    + "(boleta o factura, pago.validado → reserva confirmada → constancia), actor «Canchita (IA)» en la bitácora y "
                    + "la lectura guardada. Idempotente; 422 PAGO_NO_EN_REVISION si el pago no está en revisión.")
    @PostMapping("/pagos/{id}/validar-ia")
    public CobroRespuesta validarPorIa(@PathVariable Long id, @Valid @RequestBody SolicitudValidacionIa solicitud) {
        String lectura = solicitud.lectura().isTextual() ? solicitud.lectura().asText() : solicitud.lectura().toString();
        return pagos.validarPorIa(id, lectura, solicitud.validadoPor());
    }

    @Operation(summary = "PDF de un comprobante", description = "Lo usa notificaciones-service para adjuntarlo al correo y WhatsApp.")
    @GetMapping("/comprobantes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return Descargas.archivo(comprobantes.pdf(id), true);
    }
}
