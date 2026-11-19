package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.servicio.ConfiguracionServicio;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/configuracion")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer")
@Tag(name = "Configuración (panel)", description = "Tarifas, reglas de reserva, horario y datos de pago del complejo")
public class ConfiguracionAdminControlador {

    private final ConfiguracionServicio configuracion;

    @GetMapping
    @Operation(summary = "Ver la configuración", description = "Todas las claves {clave: valor}. ADMIN o RECEPCION.")
    public Map<String, String> ver() {
        return configuracion.todas();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar la configuración",
            description = "Envía solo las claves a cambiar. Cada valor se valida (porcentajes, horas HH:mm, celulares, RUC…) "
                    + "y también el horario en conjunto. Solo ADMIN.")
    public Map<String, String> actualizar(@RequestBody Map<String, Object> cambios) {
        return configuracion.actualizar(cambios);
    }
}
