package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.pagos.dto.CobroRespuesta;
import pe.alcanfores.pagos.dto.ListadoPagosRespuesta;
import pe.alcanfores.pagos.dto.PagoDetalleRespuesta;
import pe.alcanfores.pagos.dto.PagoResumenRespuesta;
import pe.alcanfores.pagos.dto.SolicitudCaja;
import pe.alcanfores.pagos.dto.SolicitudRechazo;
import pe.alcanfores.pagos.servicio.AlmacenComprobantes;
import pe.alcanfores.pagos.servicio.PagoServicio;

import java.time.LocalDate;

/** Panel: pagos por validar, validación/rechazo con visor de la imagen y cobro en caja (ADMIN y RECEPCION). */
@Tag(name = "Pagos (panel)", description = "Listado, detalle, validar/rechazar comprobantes y cobro en caja")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/pagos")
public class AdminPagoControlador {

    private final PagoServicio pagos;

    public AdminPagoControlador(PagoServicio pagos) {
        this.pagos = pagos;
    }

    @Operation(summary = "Listar pagos", description = "Filtros: estado, metodo, canal, desde, hasta (fecha de envío) y q (código, DNI, nombre u operación).")
    @GetMapping
    public ListadoPagosRespuesta listar(@RequestParam(required = false) String estado,
                                        @RequestParam(required = false) String metodo,
                                        @RequestParam(required = false) String canal,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @RequestParam(required = false) String q,
                                        @PageableDefault(size = 20, sort = "fechaEnvio", direction = Sort.Direction.DESC) Pageable pagina) {
        return pagos.listar(estado, metodo, canal, desde, hasta, q, pagina);
    }

    @Operation(summary = "Detalle de un pago", description = "Con datos de facturación, comprobantes emitidos y enlace a la imagen.")
    @GetMapping("/{id}")
    public PagoDetalleRespuesta detalle(@PathVariable Long id) {
        return pagos.detalle(id);
    }

    @Operation(summary = "Imagen del comprobante Yape/Plin")
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable Long id) {
        AlmacenComprobantes.Imagen imagen = pagos.imagen(id);
        return Descargas.imagenPrivada(imagen.tipoContenido(), imagen.contenido());
    }

    @Operation(summary = "Validar pago", description = "VALIDADO + boleta/factura + eventos pago.validado y comprobante.emitido.")
    @PostMapping("/{id}/validar")
    public CobroRespuesta validar(@PathVariable Long id) {
        return pagos.validar(id);
    }

    @Operation(summary = "Rechazar pago", description = "{observacion} de 10 a 255 caracteres; el cliente la recibe y puede reenviar su comprobante.")
    @PostMapping("/{id}/rechazar")
    public PagoResumenRespuesta rechazar(@PathVariable Long id, @Valid @RequestBody SolicitudRechazo solicitud) {
        return pagos.rechazar(id, solicitud.observacion());
    }

    @Operation(summary = "Cobrar en caja", description = "Adelanto, total o saldo en efectivo, Yape, Plin o POS; emite el comprobante al instante.")
    @PostMapping("/caja")
    @ResponseStatus(HttpStatus.CREATED)
    public CobroRespuesta caja(@Valid @RequestBody SolicitudCaja solicitud) {
        return pagos.cobrarEnCaja(solicitud);
    }

    @Operation(summary = "Adjuntar la foto del comprobante a un cobro en caja",
            description = "Multipart `archivo` (JPG o PNG ≤ 5 MB, ≥ 200 px). Solo para cobros en caja con Yape, Plin o POS.")
    @PostMapping(value = "/{id}/archivo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public PagoDetalleRespuesta adjuntarFoto(@PathVariable Long id,
                                             @org.springframework.web.bind.annotation.RequestParam(required = false)
                                             org.springframework.web.multipart.MultipartFile archivo) throws java.io.IOException {
        return pagos.adjuntarFotoCaja(id, archivo == null || archivo.isEmpty() ? null : archivo.getBytes());
    }
}
