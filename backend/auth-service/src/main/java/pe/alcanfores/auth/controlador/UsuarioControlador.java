package pe.alcanfores.auth.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pe.alcanfores.auth.dto.CambiarEstadoSolicitud;
import pe.alcanfores.auth.dto.CrearUsuarioSolicitud;
import pe.alcanfores.auth.dto.EditarUsuarioSolicitud;
import pe.alcanfores.auth.dto.FiltroUsuarios;
import pe.alcanfores.auth.dto.RestablecerContrasenaSolicitud;
import pe.alcanfores.auth.dto.UsuarioRespuesta;
import pe.alcanfores.auth.modelo.EstadoUsuario;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.auth.servicio.UsuarioServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios", description = "Usuarios del panel (solo ADMIN)")
public class UsuarioControlador {

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Filtros: q (nombre, usuario o correo), rol y estado. Paginado.")
    public PaginaRespuesta<UsuarioRespuesta> listar(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Rol rol,
                                                    @RequestParam(required = false) EstadoUsuario estado,
                                                    @PageableDefault(size = 20) Pageable pagina) {
        return servicio.listar(new FiltroUsuarios(q, rol, estado), pagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un usuario")
    public UsuarioRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Usuario y correo únicos (409 si se repiten).")
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody CrearUsuarioSolicitud solicitud) {
        UsuarioRespuesta creado = servicio.crear(solicitud);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri()).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar datos y rol",
            description = "No puedes quitarte tu propio rol ADMIN; siempre debe quedar al menos un ADMIN activo.")
    public UsuarioRespuesta editar(@PathVariable Long id, @Valid @RequestBody EditarUsuarioSolicitud solicitud,
                                   @AuthenticationPrincipal UsuarioToken actor) {
        return servicio.editar(id, solicitud, actor);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar", description = "No puedes desactivarte a ti mismo.")
    public UsuarioRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoSolicitud solicitud,
                                          @AuthenticationPrincipal UsuarioToken actor) {
        return servicio.cambiarEstado(id, solicitud.estado(), actor);
    }

    @PostMapping("/{id}/restablecer-contrasena")
    @Operation(summary = "Restablecer contraseña", description = "Asigna una contraseña nueva y desbloquea la cuenta.")
    public ResponseEntity<Map<String, String>> restablecer(@PathVariable Long id,
                                                           @Valid @RequestBody RestablecerContrasenaSolicitud solicitud) {
        servicio.restablecerContrasena(id, solicitud.nueva());
        return ResponseEntity.ok(Map.of("mensaje", "La contraseña se restableció y la cuenta quedó desbloqueada."));
    }
}
