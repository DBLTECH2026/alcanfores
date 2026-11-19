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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.pagos.dto.ComprobanteRespuesta;
import pe.alcanfores.pagos.dto.SolicitudAnulacion;
import pe.alcanfores.pagos.servicio.ComprobanteServicio;

import java.time.LocalDate;

@Tag(name = "Comprobantes (panel)", description = "Boletas, facturas y notas de crédito simuladas")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/comprobantes")
public class AdminComprobanteControlador {

    private final ComprobanteServicio comprobantes;

    public AdminComprobanteControlador(ComprobanteServicio comprobantes) {
        this.comprobantes = comprobantes;
    }

    @Operation(summary = "Listar comprobantes", description = "Filtros: tipo (BOLETA|FACTURA|NOTA_CREDITO), estado, desde, hasta y q (serie-número, documento, nombre o reserva).")
    @GetMapping
    public PaginaRespuesta<ComprobanteRespuesta> listar(@RequestParam(required = false) String tipo,
                                                        @RequestParam(required = false) String estado,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                        @RequestParam(required = false) String q,
                                                        @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pagina) {
        return comprobantes.listar(tipo, estado, desde, hasta, q, pagina);
    }

    @Operation(summary = "Detalle de un comprobante")
    @GetMapping("/{id}")
    public ComprobanteRespuesta obtener(@PathVariable Long id) {
        return comprobantes.obtener(id);
    }

    @Operation(summary = "PDF A4 del comprobante")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return Descargas.archivo(comprobantes.pdf(id), true);
    }

    @Operation(summary = "XML UBL 2.1 del comprobante")
    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        return Descargas.archivo(comprobantes.xml(id), false);
    }

    @Operation(summary = "Anular con nota de crédito (solo ADMIN)", description = "{motivo} de 10 a 255 caracteres; emite BC01/FC01 que referencia al original.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/anular")
    @ResponseStatus(HttpStatus.CREATED)
    public ComprobanteRespuesta anular(@PathVariable Long id, @Valid @RequestBody SolicitudAnulacion solicitud) {
        return comprobantes.anular(id, solicitud.motivo());
    }
}
