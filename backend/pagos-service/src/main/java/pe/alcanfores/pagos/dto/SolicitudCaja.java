package pe.alcanfores.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.comun.validacion.CodigoReserva;

import java.math.BigDecimal;

/** Cobro presencial en caja (adelanto, total o saldo). */
public record SolicitudCaja(
        @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigoReserva,
        @NotNull(message = Mensajes.TIPO_PAGO_CAJA) @Pattern(regexp = "ADELANTO|TOTAL|SALDO", message = Mensajes.TIPO_PAGO_CAJA) String tipoPago,
        @NotNull(message = Mensajes.METODO) @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA_POS", message = Mensajes.METODO) String metodo,
        @NotNull(message = Mensajes.MONTO) @DecimalMin(value = "0.01", message = Mensajes.MONTO)
        @Digits(integer = 6, fraction = 2, message = Mensajes.MONTO_DECIMALES) BigDecimal monto,
        @Pattern(regexp = "\\d{6,12}", message = Mensajes.OPERACION) String codigoOperacion,
        @Valid DatosComprobanteSolicitud comprobante) {
}
