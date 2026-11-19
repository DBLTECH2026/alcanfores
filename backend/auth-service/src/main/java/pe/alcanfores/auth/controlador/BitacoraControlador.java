package pe.alcanfores.auth.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.auth.dto.BitacoraRespuesta;
import pe.alcanfores.auth.dto.FiltroBitacora;
import pe.alcanfores.auth.dto.OpcionesBitacora;
import pe.alcanfores.auth.servicio.BitacoraServicio;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/bitacora")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Bitácora", description = "Auditoría central de todos los microservicios (solo ADMIN)")
public class BitacoraControlador {

    private final BitacoraServicio servicio;

    public BitacoraControlador(BitacoraServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Buscar en la bitácora",
            description = "Filtros opcionales: servicio, actor (contiene), accion, entidad, desde y hasta (YYYY-MM-DD). Paginado, lo más reciente primero.")
    public PaginaRespuesta<BitacoraRespuesta> buscar(
            @RequestParam(required = false) String servicio,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pagina) {
        return this.servicio.buscar(new FiltroBitacora(servicio, actor, accion, entidad, desde, hasta), pagina);
    }

    @GetMapping("/opciones")
    @Operation(summary = "Valores para los filtros", description = "Servicios y acciones que ya aparecen en la bitácora.")
    public OpcionesBitacora opciones() {
        return servicio.opciones();
    }
}
