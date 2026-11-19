package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.DiaCalendario;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.DisponibilidadRespuesta;
import pe.alcanfores.reservas.servicio.DisponibilidadServicio;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/disponibilidad")
@RequiredArgsConstructor
@Tag(name = "Disponibilidad", description = "Turnos libres por cancha y calendario (público)")
public class DisponibilidadControlador {

    private final DisponibilidadServicio servicio;

    @GetMapping
    @Operation(summary = "Turnos de un día", description = "Estados: DISPONIBLE, OCUPADO, BLOQUEADO o PASADO (empieza en menos de 30 min).")
    public DisponibilidadRespuesta disponibilidad(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                                  @RequestParam(required = false) Long idCancha) {
        return servicio.disponibilidad(fecha, idCancha);
    }

    @GetMapping("/calendario")
    @Operation(summary = "Turnos libres por día", description = "Para pintar el calendario: [{fecha, libres, total}].")
    public List<DiaCalendario> calendario(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) Integer dias,
                                          @RequestParam(required = false) Long idCancha) {
        return servicio.calendario(desde, dias, idCancha);
    }
}
