package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteItem;
import pe.alcanfores.reservas.servicio.MisReservasServicio;

/** Portal del cliente con cuenta (token CLIENTE): sus reservas por el DNI del token. */
@RestController
@RequestMapping("/api/cliente/reservas")
@RequiredArgsConstructor
@Tag(name = "Mis reservas", description = "Cliente con cuenta: todas las reservas de su DNI, incluso las anteriores a la cuenta")
public class MisReservasControlador {

    private final MisReservasServicio servicio;

    @GetMapping
    @Operation(summary = "Mis reservas", description = "Paginado (size 1–50, por defecto 10), las más recientes primero; "
            + "estado opcional PENDIENTE | CONFIRMADA | CANCELADA.")
    public PaginaRespuesta<ReservaClienteItem> listar(@RequestParam(required = false) String estado,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return servicio.listar(SeguridadUtil.requerido().dni(), estado, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Detalle de una de mis reservas", description = "Igual que «Mi reserva» con código + DNI, más "
            + "«resenada». 404 si la reserva no es de tu DNI.")
    public ReservaClienteDetalle detalle(@PathVariable String codigo) {
        return servicio.detalle(SeguridadUtil.requerido().dni(), codigo);
    }
}
