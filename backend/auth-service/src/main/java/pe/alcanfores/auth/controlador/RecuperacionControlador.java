package pe.alcanfores.auth.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.auth.dto.RecuperarDtos.EnviarSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.EnvioRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.OpcionesSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.RestablecerSolicitud;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificacionRespuesta;
import pe.alcanfores.auth.dto.RecuperarDtos.VerificarSolicitud;
import pe.alcanfores.auth.servicio.RecuperacionServicio;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/recuperar")
@Tag(name = "Recuperar contraseña",
        description = "Código de 6 dígitos por correo o WhatsApp para clientes del portal y usuarios del panel (públicos)")
public class RecuperacionControlador {

    private final RecuperacionServicio servicio;

    public RecuperacionControlador(RecuperacionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/opciones")
    @Operation(summary = "Por dónde puedo recibir el código",
            description = "Cliente: DNI o correo; panel: usuario o correo. Destinos enmascarados. 404 CUENTA_NO_ENCONTRADA.")
    public OpcionesRespuesta opciones(@Valid @RequestBody OpcionesSolicitud solicitud) {
        return servicio.opciones(solicitud);
    }

    @PostMapping("/enviar")
    @Operation(summary = "Enviar el código",
            description = "Vence en 10 min; reenviar tras 60 s; máximo 3 envíos cada 15 min (429 DEMASIADOS_ENVIOS). "
                    + "simulado = el canal aún no está configurado.")
    public EnvioRespuesta enviar(@Valid @RequestBody EnviarSolicitud solicitud) {
        return servicio.enviar(solicitud);
    }

    @PostMapping("/verificar")
    @Operation(summary = "Verificar el código",
            description = "Devuelve un permiso de un solo uso (10 min). 422 CODIGO_INCORRECTO, CODIGO_VENCIDO o DEMASIADOS_INTENTOS.")
    public VerificacionRespuesta verificar(@Valid @RequestBody VerificarSolicitud solicitud) {
        return servicio.verificar(solicitud);
    }

    @PostMapping("/restablecer")
    @Operation(summary = "Elegir la contraseña nueva",
            description = "Mismas reglas de siempre; desbloquea la cuenta y avisa «Tu contraseña se cambió». 422 TOKEN_INVALIDO.")
    public Map<String, String> restablecer(@Valid @RequestBody RestablecerSolicitud solicitud) {
        servicio.restablecer(solicitud);
        return Map.of("mensaje", "Tu contraseña se cambió. Ya puedes iniciar sesión con la nueva.");
    }
}
