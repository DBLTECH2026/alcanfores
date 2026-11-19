package pe.alcanfores.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.catalogo.dto.CanchaInternaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaExisteRespuesta;
import pe.alcanfores.catalogo.servicio.CanchaServicio;
import pe.alcanfores.catalogo.servicio.ConfiguracionServicio;
import pe.alcanfores.catalogo.servicio.ResenaServicio;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Consultas entre microservicios (cabecera X-Interno-Token). El gateway nunca enruta /interno/**. */
@RestController
@RequestMapping("/interno")
@RequiredArgsConstructor
@Tag(name = "Interno", description = "Uso exclusivo de reservas-service y pagos-service")
public class InternoControlador {

    private final CanchaServicio canchas;
    private final ConfiguracionServicio configuracion;
    private final ResenaServicio resenas;

    @GetMapping("/canchas")
    @Operation(summary = "Todas las canchas (activas e inactivas) con precios y estado")
    public List<CanchaInternaRespuesta> canchas() {
        return canchas.listarInternas();
    }

    @GetMapping("/canchas/{id}")
    @Operation(summary = "Una cancha por id")
    public CanchaInternaRespuesta cancha(@PathVariable Long id) {
        return canchas.interna(id);
    }

    @GetMapping("/resenas/{codigoReserva}")
    @Operation(summary = "¿La reserva ya tiene reseña?", description = "Lo usa reservas-service para no ofrecer "
            + "«Calificar la cancha» a una reserva que ya fue calificada.")
    public ResenaExisteRespuesta resenaDeReserva(@PathVariable String codigoReserva) {
        String codigo = codigoReserva.trim().toUpperCase(Locale.ROOT);
        return new ResenaExisteRespuesta(codigo, resenas.existeParaReserva(codigo));
    }

    @GetMapping("/configuracion")
    @Operation(summary = "Toda la configuración {clave: valor}")
    public Map<String, String> configuracion() {
        return configuracion.todas();
    }
}
