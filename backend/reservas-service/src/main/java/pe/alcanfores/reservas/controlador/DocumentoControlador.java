package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;
import pe.alcanfores.reservas.servicio.DocumentoServicio;

@RestController
@RequestMapping("/api/documentos")
@RequiredArgsConstructor
@Tag(name = "Documentos", description = "Consulta de DNI (RENIEC) y RUC (SUNAT) por Factiliza, con caché de 30 días")
public class DocumentoControlador {

    private final DocumentoServicio servicio;

    @GetMapping("/dni/{dni}")
    @Operation(summary = "Consultar DNI", description = "404 DNI_NO_ENCONTRADO · 503 CONSULTA_NO_DISPONIBLE (el front deja escribir a mano).")
    public DniRespuesta dni(@PathVariable String dni) {
        return servicio.consultarDni(dni);
    }

    @GetMapping("/ruc/{ruc}")
    @Operation(summary = "Consultar RUC", description = "Valida el dígito verificador antes de consultar a SUNAT.")
    public RucRespuesta ruc(@PathVariable String ruc) {
        return servicio.consultarRuc(ruc);
    }
}
