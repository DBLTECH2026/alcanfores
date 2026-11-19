package pe.alcanfores.notificaciones.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.notificaciones.dto.ConectarWhatsappSolicitud;
import pe.alcanfores.notificaciones.dto.ConexionWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.EstadoWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.MensajeRespuesta;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.dto.PruebaWhatsappSolicitud;
import pe.alcanfores.notificaciones.servicio.WhatsappServicio;

@Tag(name = "WhatsApp", description = "Conexión del WhatsApp del complejo con Evolution API (QR) y mensajes de prueba")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/admin/whatsapp")
@PreAuthorize("hasRole('ADMIN')")
public class WhatsappControlador {

    private final WhatsappServicio servicio;

    public WhatsappControlador(WhatsappServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Estado de la conexión", description = "estado: open, connecting, close o NO_CONFIGURADO.")
    @GetMapping("/estado")
    public EstadoWhatsappRespuesta estado() {
        return servicio.estado();
    }

    @Operation(summary = "Pide el QR para vincular el WhatsApp",
            description = "Crea la instancia si no existe. Con «telefono» devuelve también el código de emparejamiento.")
    @PostMapping("/conectar")
    public ConexionWhatsappRespuesta conectar(@Valid @RequestBody(required = false) ConectarWhatsappSolicitud solicitud) {
        return servicio.conectar(solicitud == null ? null : solicitud.telefono());
    }

    @Operation(summary = "Cierra la sesión de WhatsApp en Evolution API")
    @PostMapping("/desconectar")
    public MensajeRespuesta desconectar() {
        return servicio.desconectar();
    }

    @Operation(summary = "Envía un mensaje de prueba",
            description = "Queda en la bandeja con el evento whatsapp.prueba (SIMULADO si Evolution no está configurado).")
    @PostMapping("/prueba")
    public NotificacionDetalle prueba(@Valid @RequestBody PruebaWhatsappSolicitud solicitud) {
        return servicio.prueba(solicitud.telefono(), solicitud.mensaje());
    }
}
