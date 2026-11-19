package pe.alcanfores.auth.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.auth.dto.CambiarContrasenaSolicitud;
import pe.alcanfores.auth.dto.ClienteSesion;
import pe.alcanfores.auth.dto.EditarClienteSolicitud;
import pe.alcanfores.auth.dto.LoginClienteRespuesta;
import pe.alcanfores.auth.dto.LoginClienteSolicitud;
import pe.alcanfores.auth.dto.RegistroClienteSolicitud;
import pe.alcanfores.auth.servicio.CuentaClienteServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/clientes")
@Tag(name = "Cuentas de clientes", description = "Registro, inicio de sesión y datos del cliente del portal («Mis reservas»)")
public class CuentaClienteControlador {

    private final CuentaClienteServicio servicio;

    public CuentaClienteControlador(CuentaClienteServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/registro")
    @Operation(summary = "Crear mi cuenta",
            description = "Deja la sesión iniciada (mismo cuerpo que el login). 409 si el DNI o el correo ya tienen cuenta.")
    public ResponseEntity<LoginClienteRespuesta> registrar(@Valid @RequestBody RegistroClienteSolicitud solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(solicitud));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con DNI o correo",
            description = "JWT con rol CLIENTE y el DNI. 401 si no calza, 423 tras 5 intentos fallidos (15 min).")
    public LoginClienteRespuesta login(@Valid @RequestBody LoginClienteSolicitud solicitud) {
        return servicio.iniciarSesion(solicitud);
    }

    @GetMapping("/yo")
    @Operation(summary = "Mi cuenta", description = "Datos del cliente dueño del token.")
    public ClienteSesion yo(@AuthenticationPrincipal UsuarioToken cliente) {
        return servicio.actual(cliente);
    }

    @PutMapping("/yo")
    @Operation(summary = "Editar mis datos", description = "Nombres, apellidos, correo y celular (el DNI no se cambia).")
    public ClienteSesion editar(@AuthenticationPrincipal UsuarioToken cliente,
                                @Valid @RequestBody EditarClienteSolicitud solicitud) {
        return servicio.editar(cliente, solicitud);
    }

    @PostMapping("/cambiar-contrasena")
    @Operation(summary = "Cambiar mi contraseña",
            description = "8 a 64 caracteres, con al menos una mayúscula, una minúscula y un número.")
    public ResponseEntity<Map<String, String>> cambiarContrasena(@AuthenticationPrincipal UsuarioToken cliente,
                                                                 @Valid @RequestBody CambiarContrasenaSolicitud solicitud) {
        servicio.cambiarContrasena(cliente, solicitud);
        return ResponseEntity.ok(Map.of("mensaje", "Tu contraseña se cambió correctamente."));
    }
}
