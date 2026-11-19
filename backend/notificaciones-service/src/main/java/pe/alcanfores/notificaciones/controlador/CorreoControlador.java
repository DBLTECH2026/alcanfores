package pe.alcanfores.notificaciones.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.notificaciones.dto.CorreoDtos.ConfigCorreoRespuesta;
import pe.alcanfores.notificaciones.dto.CorreoDtos.GuardarCorreoSolicitud;
import pe.alcanfores.notificaciones.dto.CorreoDtos.PruebaCorreoRespuesta;
import pe.alcanfores.notificaciones.dto.CorreoDtos.PruebaCorreoSolicitud;
import pe.alcanfores.notificaciones.servicio.CorreoServicio;

@Tag(name = "Correo", description = "Servidor de correo (SMTP) configurable desde el panel y correo de prueba")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/correo")
@PreAuthorize("hasRole('ADMIN')")
public class CorreoControlador {

    private final CorreoServicio servicio;

    public CorreoControlador(CorreoServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Configuración vigente del correo",
            description = "origen: PANEL, VARIABLES (MAIL_* del servidor) o NINGUNO (simulado). Nunca devuelve la contraseña.")
    @GetMapping
    public ConfigCorreoRespuesta obtener() {
        return servicio.obtener();
    }

    @Operation(summary = "Guarda el servidor de correo",
            description = "Hostinger: smtp.hostinger.com, 465 SSL o 587 STARTTLS. clave vacía = conservar la guardada.")
    @PutMapping
    public ConfigCorreoRespuesta guardar(@Valid @RequestBody GuardarCorreoSolicitud solicitud) {
        return servicio.guardar(solicitud);
    }

    @Operation(summary = "Envía un correo de prueba ahora",
            description = "200 {enviado:true} o 422 CORREO_NO_ENVIADO con la causa (usuario y clave, servidor, puerto o SSL).")
    @PostMapping("/prueba")
    public PruebaCorreoRespuesta probar(@Valid @RequestBody PruebaCorreoSolicitud solicitud) {
        return servicio.probar(solicitud.destino());
    }
}
