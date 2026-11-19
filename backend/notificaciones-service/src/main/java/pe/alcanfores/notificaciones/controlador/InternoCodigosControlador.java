package pe.alcanfores.notificaciones.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.notificaciones.dto.CorreoDtos.CodigoInternoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.EnvioCodigoRespuesta;
import pe.alcanfores.notificaciones.servicio.CuentaAvisoServicio;

@Tag(name = "Interno", description = "Solo entre microservicios (X-Interno-Token)")
@RestController
@RequestMapping("/interno/codigos")
public class InternoCodigosControlador {

    private final CuentaAvisoServicio servicio;

    public InternoCodigosControlador(CuentaAvisoServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Envía un código de verificación o el aviso de contraseña cambiada (síncrono)",
            description = "Lo usa auth-service. estado: ENVIADO, SIMULADO o FALLIDO. El código se guarda enmascarado.")
    @PostMapping
    public EnvioCodigoRespuesta enviar(@Valid @RequestBody CodigoInternoSolicitud solicitud) {
        return servicio.enviar(solicitud);
    }
}
