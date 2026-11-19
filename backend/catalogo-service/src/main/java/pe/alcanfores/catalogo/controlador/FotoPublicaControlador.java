package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.servicio.FotoServicio;

import java.time.Duration;

@RestController
@RequestMapping("/api/catalogo/fotos")
@RequiredArgsConstructor
@Tag(name = "Fotos", description = "Imágenes públicas de las canchas")
public class FotoPublicaControlador {

    static final Duration CACHE = Duration.ofDays(7);

    private final FotoServicio fotos;

    @GetMapping("/{archivo}")
    @Operation(summary = "Foto de una cancha",
            description = "Solo nombres UUID generados por el sistema (no acepta rutas). Se cachea 7 días.")
    public ResponseEntity<Resource> foto(@PathVariable String archivo) {
        FotoServicio.ArchivoFoto f = fotos.leer(archivo);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.tipoContenido()))
                .body(f.recurso());
    }
}
