package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaSolicitud;
import pe.alcanfores.catalogo.dto.EstadoCanchaSolicitud;
import pe.alcanfores.catalogo.dto.FotoActualizacionSolicitud;
import pe.alcanfores.catalogo.dto.FotoRespuesta;
import pe.alcanfores.catalogo.servicio.CanchaServicio;
import pe.alcanfores.catalogo.servicio.FotoServicio;

import java.util.List;

@RestController
@RequestMapping("/api/admin/canchas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearer")
@Tag(name = "Canchas (panel)", description = "Alta, edición, estado y fotos de las canchas. Solo ADMIN.")
public class CanchaAdminControlador {

    private final CanchaServicio canchas;
    private final FotoServicio fotos;

    @GetMapping
    @Operation(summary = "Listar todas las canchas", description = "Incluye las inactivas, con fotos y reseñas resumidas.")
    public List<CanchaDetalleRespuesta> listar() {
        return canchas.listarTodas();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una cancha")
    public CanchaDetalleRespuesta detalle(@PathVariable Long id) {
        return canchas.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cancha", description = "Publica cancha.actualizada. 409 si el nombre ya existe.")
    public CanchaDetalleRespuesta crear(@Valid @RequestBody CanchaSolicitud solicitud) {
        return canchas.crear(solicitud);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar cancha", description = "Publica cancha.actualizada (reservas repone el precio de los turnos libres).")
    public CanchaDetalleRespuesta editar(@PathVariable Long id, @Valid @RequestBody CanchaSolicitud solicitud) {
        return canchas.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar", description = "INACTIVA bloquea en reservas los turnos futuros libres.")
    public CanchaDetalleRespuesta estado(@PathVariable Long id, @Valid @RequestBody EstadoCanchaSolicitud solicitud) {
        return canchas.cambiarEstado(id, solicitud.estado());
    }

    @PostMapping(path = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir foto", description = "JPG, PNG o WebP de hasta 5 MB (se verifican los bytes). La primera es la principal.")
    public FotoRespuesta subirFoto(@PathVariable Long id,
                                   @RequestPart("archivo") MultipartFile archivo,
                                   @RequestParam(required = false)
                                   @Size(max = 120, message = "La leyenda admite hasta 120 caracteres.") String leyenda) {
        return fotos.subir(id, archivo, leyenda);
    }

    @PatchMapping("/{id}/fotos/{idFoto}")
    @Operation(summary = "Editar foto", description = "Leyenda, orden o marcarla como principal (solo una por cancha).")
    public FotoRespuesta editarFoto(@PathVariable Long id, @PathVariable Long idFoto,
                                    @Valid @RequestBody FotoActualizacionSolicitud solicitud) {
        return fotos.actualizar(id, idFoto, solicitud);
    }

    @DeleteMapping("/{id}/fotos/{idFoto}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar foto", description = "Borra el registro y el archivo; si era la principal, pasa a la siguiente.")
    public void borrarFoto(@PathVariable Long id, @PathVariable Long idFoto) {
        fotos.eliminar(id, idFoto);
    }
}
