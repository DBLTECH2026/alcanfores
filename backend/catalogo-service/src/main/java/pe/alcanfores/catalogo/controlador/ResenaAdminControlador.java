package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.dto.EstadoResenaSolicitud;
import pe.alcanfores.catalogo.dto.RespuestaResenaSolicitud;
import pe.alcanfores.catalogo.dto.ResenaAdminRespuesta;
import pe.alcanfores.catalogo.dto.ResenaComplejoSolicitud;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.catalogo.servicio.ResenaServicio;
import pe.alcanfores.comun.util.PaginaRespuesta;

@RestController
@RequestMapping("/api/admin/resenas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Reseñas (panel)", description = "Moderación, respuesta y reseñas del complejo. Solo ADMIN.")
public class ResenaAdminControlador {

    private final ResenaServicio resenas;

    @GetMapping
    @Operation(summary = "Listar reseñas", description = "Filtros: estado, idCancha, puntuacion. Más recientes primero.")
    public PaginaRespuesta<ResenaAdminRespuesta> listar(
            @RequestParam(required = false) EstadoResena estado,
            @RequestParam(required = false) Long idCancha,
            @RequestParam(required = false) @Min(value = 1, message = "La puntuación va de 1 a 5.")
            @Max(value = 5, message = "La puntuación va de 1 a 5.") Integer puntuacion,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página empieza en 0.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño de página va de 1 a 100.")
            @Max(value = 100, message = "El tamaño de página va de 1 a 100.") int size) {
        return resenas.listarAdmin(estado, idCancha, puntuacion, page, size);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Ocultar o publicar una reseña")
    public ResenaAdminRespuesta estado(@PathVariable Long id, @Valid @RequestBody EstadoResenaSolicitud solicitud) {
        return resenas.cambiarEstado(id, solicitud.estado());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una reseña del complejo", description = "422 RESENA_DE_CLIENTE si es de un cliente.")
    public ResenaAdminRespuesta editar(@PathVariable Long id, @Valid @RequestBody ResenaComplejoSolicitud solicitud) {
        return resenas.editarDelComplejo(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar una reseña del complejo", description = "422 RESENA_DE_CLIENTE si es de un cliente (esas se ocultan).")
    public void borrar(@PathVariable Long id) {
        resenas.eliminarDelComplejo(id);
    }

    @PostMapping("/{id}/respuesta")
    @Operation(summary = "Responder una reseña", description = "La respuesta (10 a 500 caracteres) se muestra bajo la reseña.")
    public ResenaAdminRespuesta responder(@PathVariable Long id, @Valid @RequestBody RespuestaResenaSolicitud solicitud) {
        return resenas.responder(id, solicitud.respuesta());
    }
}
