package pe.alcanfores.pagos.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.pagos.dto.Mensajes;
import pe.alcanfores.pagos.servicio.ComprobanteServicio;

@Tag(name = "Comprobantes (cliente)", description = "Descarga de la boleta, factura o nota de crédito con código de reserva + DNI")
@RestController
@RequestMapping("/api/comprobantes")
public class ComprobantePublicoControlador {

    private final ComprobanteServicio comprobantes;

    public ComprobantePublicoControlador(ComprobanteServicio comprobantes) {
        this.comprobantes = comprobantes;
    }

    @Operation(summary = "PDF A4 del comprobante", description = "Representación impresa simulada (sin validez tributaria).")
    @GetMapping("/{serieNumero}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String serieNumero,
                                      @RequestParam(required = false) @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigo,
                                      @RequestParam(required = false) @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return Descargas.archivo(comprobantes.pdfParaCliente(serieNumero, codigo, dni), true);
    }

    @Operation(summary = "XML UBL 2.1 del comprobante", description = "Sin firma digital; incluye un comentario de documento simulado.")
    @GetMapping("/{serieNumero}.xml")
    public ResponseEntity<byte[]> xml(@PathVariable String serieNumero,
                                      @RequestParam(required = false) @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigo,
                                      @RequestParam(required = false) @NotNull(message = Mensajes.DNI) @Dni String dni) {
        return Descargas.archivo(comprobantes.xmlParaCliente(serieNumero, codigo, dni), false);
    }
}
