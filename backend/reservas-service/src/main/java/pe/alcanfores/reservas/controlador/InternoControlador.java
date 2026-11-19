package pe.alcanfores.reservas.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.Dni;
import pe.alcanfores.reservas.dto.InternoDtos.ElegibilidadResena;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaDelCliente;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaInterna;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaSemilla;
import pe.alcanfores.reservas.dto.InternoDtos.ReservasVigentes;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.servicio.InternoServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/interno/reservas")
@RequiredArgsConstructor
@Tag(name = "Interno", description = "Solo para otros microservicios (cabecera X-Interno-Token)")
public class InternoControlador {

    private final InternoServicio servicio;

    @GetMapping("/vigentes")
    @Operation(summary = "Reservas vigentes de un día", description = "PENDIENTE o CONFIRMADA con algún turno esa fecha en "
            + "esas canchas (sin idsCancha = todas). Catálogo lo usa antes de cerrar un día (409 DIA_CON_RESERVAS).")
    public ReservasVigentes vigentes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                     @RequestParam(required = false) List<Long> idsCancha) {
        return servicio.vigentes(fecha, idsCancha);
    }

    @GetMapping("/por-cliente")
    @Operation(summary = "Reservas de un cliente por DNI y celular", description = "Canchita (WhatsApp) reconoce al cliente: "
            + "reservas del DNI desde hace 30 días en adelante, por fecha y hora, SOLO si el celular registrado del cliente "
            + "es ese (se aceptan 9 dígitos, 51… o +51…). Si el DNI no existe o el celular no calza, lista vacía.")
    public List<ReservaDelCliente> porCliente(
            @RequestParam @NotNull(message = Mensajes.DNI) @Dni String dni,
            @RequestParam @NotNull(message = Mensajes.CELULAR) @Celular(message = Mensajes.CELULAR) String telefono) {
        return servicio.porCliente(dni, telefono);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Datos de la reserva", description = "DatosReserva + estado, estadoPago, montoPagado, saldo e inicio.")
    public ReservaInterna reserva(@PathVariable String codigo) {
        return servicio.reserva(codigo);
    }

    @GetMapping("/{codigo}/elegible-resena")
    @Operation(summary = "¿Puede reseñar?", description = "La usa catalogo-service antes de guardar una reseña verificada.")
    public ElegibilidadResena elegible(@PathVariable String codigo, @RequestParam String dni,
                                       @RequestParam(required = false) Long idCancha) {
        return servicio.elegibleResena(codigo, dni, idCancha);
    }

    @GetMapping("/{codigo}/constancia.pdf")
    @Operation(summary = "Constancia PDF", description = "Para adjuntarla en correos y WhatsApp.")
    public ResponseEntity<byte[]> constancia(@PathVariable String codigo) {
        return Archivos.pdf(servicio.constancia(codigo), "constancia-" + codigo.trim().toUpperCase() + ".pdf");
    }

    @GetMapping("/proximas")
    @Operation(summary = "Reservas confirmadas por empezar", description = "desde/hasta en ISO (2026-11-20T18:00:00); por defecto las próximas 24 h.")
    public List<ReservaInterna> proximas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return servicio.proximas(desde, hasta);
    }

    @GetMapping("/para-semilla")
    @Operation(summary = "Reservas sembradas", description = "pagos-service crea pagos y comprobantes coherentes con la semilla.")
    public List<ReservaSemilla> paraSemilla() {
        return servicio.paraSemilla();
    }
}
