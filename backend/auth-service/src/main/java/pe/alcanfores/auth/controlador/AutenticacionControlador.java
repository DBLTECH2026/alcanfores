package pe.alcanfores.auth.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.EditarMisDatosSolicitud;
import pe.alcanfores.auth.dto.LoginRespuesta;
import pe.alcanfores.auth.dto.LoginSolicitud;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.servicio.AutenticacionServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión del panel y datos del usuario conectado")
public class AutenticacionControlador {

    private final AutenticacionServicio servicio;

    public AutenticacionControlador(AutenticacionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un JWT de 8 horas. 401 si las credenciales no calzan, 423 tras 5 intentos fallidos (15 min).")
    public LoginRespuesta login(@Valid @RequestBody LoginSolicitud solicitud) {
        return servicio.iniciarSesion(solicitud);
    }

    @GetMapping("/yo")
    @Operation(summary = "Usuario conectado", description = "Datos del dueño del token (verifica que siga activo).")
    public UsuarioRespuesta yo(@AuthenticationPrincipal UsuarioToken usuario) {
        return servicio.usuarioActual(usuario);
    }

    @PutMapping("/yo")
    @Operation(summary = "Editar mis datos",
            description = "Celular (opcional; vacío = quitarlo) y correo. Con celular puedes recibir por WhatsApp el código "
                    + "para cambiar la contraseña.")
    public UsuarioRespuesta editarMisDatos(@AuthenticationPrincipal UsuarioToken usuario,
                                           @Valid @RequestBody EditarMisDatosSolicitud solicitud) {
        return servicio.editarMisDatos(usuario, solicitud);
    }

    @PostMapping("/cambiar-contrasena")
    @Operation(summary = "Cambiar mi contraseña",
            description = "8 a 64 caracteres, con al menos una mayúscula, una minúscula y un número.")
    public ResponseEntity<Map<String, String>> cambiarContrasena(@AuthenticationPrincipal UsuarioToken usuario,
                                                                 @Valid @RequestBody CambiarContrasenaSolicitud solicitud) {
        servicio.cambiarContrasena(usuario, solicitud);
        return ResponseEntity.ok(Map.of("mensaje", "Tu contraseña se cambió correctamente."));
    }
}
