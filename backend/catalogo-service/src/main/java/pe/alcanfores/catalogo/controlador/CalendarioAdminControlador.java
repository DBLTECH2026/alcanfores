package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.dto.CalendarioDtos.CargaFeriadosRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialSolicitud;
import pe.alcanfores.catalogo.servicio.CalendarioServicio;

import java.util.List;

/** Feriados y días no laborables. Ver: ADMIN o RECEPCION (la agenda los muestra); cambiar: solo ADMIN. */
@RestController
@RequestMapping("/api/admin/calendario")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer")
@Tag(name = "Calendario (panel)", description = "Feriados de Perú y días no laborables; cada cambio avisa a reservas.")
public class CalendarioAdminControlador {

    private final CalendarioServicio calendario;

    @GetMapping
    @Operation(summary = "Días especiales del año", description = "Ordenados por fecha; anio por defecto = el actual.")
    public List<DiaEspecialRespuesta> listar(@RequestParam(required = false) Integer anio) {
        return calendario.delAnio(anio);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Agregar feriado o día no laborable", description = "canchas solo en NO_LABORABLE (vacío = todas). "
            + "409 DIA_ESPECIAL_DUPLICADO si la fecha ya está; 409 DIA_CON_RESERVAS si cerraría canchas con reservas.")
    public DiaEspecialRespuesta crear(@Valid @RequestBody DiaEspecialSolicitud solicitud) {
        return calendario.crear(solicitud);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar un día especial", description = "Mismas reglas que al crear.")
    public DiaEspecialRespuesta editar(@PathVariable Long id, @Valid @RequestBody DiaEspecialSolicitud solicitud) {
        return calendario.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Quitar un día especial", description = "Reservas vuelve a abrir los turnos de ese día.")
    public void borrar(@PathVariable Long id) {
        calendario.eliminar(id);
    }

    @PostMapping("/feriados-peru")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cargar los feriados nacionales de Perú", description = "Del año indicado (por defecto el actual), "
            + "sin duplicar: una fecha que ya tiene un día especial se respeta.")
    public CargaFeriadosRespuesta feriadosPeru(@RequestParam(required = false) Integer anio) {
        return calendario.cargarFeriadosPeru(anio);
    }
}
