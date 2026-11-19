package pe.alcanfores.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;

/** Crear un pago en línea (pasarela beta o preferencia de Mercado Pago). */
public record SolicitudIntento(
        @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigoReserva,
        @NotNull(message = Mensajes.DNI) @Dni String dni,
        @NotNull(message = Mensajes.TIPO_PAGO) @Pattern(regexp = "ADELANTO|TOTAL", message = Mensajes.TIPO_PAGO) String tipoPago,
        @Valid DatosComprobanteSolicitud comprobante) {
}
