package pe.alcanfores.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.validacion.CodigoReserva;

import java.math.BigDecimal;

/**
 * {@code POST /interno/pagos/caja}: lo usa reservas-service para la reserva manual con pago inicial (después de su
 * commit). Además del cobro trae quién lo registró ({@code registradoPor} = id del administrador,
 * {@code registradoPorUsuario} = su usuario) y, opcionalmente, la {@code reserva} (DatosReserva) para no consultarla.
 */
public record SolicitudCajaInterna(
        @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigoReserva,
        @NotNull(message = Mensajes.TIPO_PAGO_CAJA) @Pattern(regexp = "ADELANTO|TOTAL|SALDO", message = Mensajes.TIPO_PAGO_CAJA) String tipoPago,
        @NotNull(message = Mensajes.METODO) @Pattern(regexp = "EFECTIVO|YAPE|PLIN|TARJETA_POS", message = Mensajes.METODO) String metodo,
        @NotNull(message = Mensajes.MONTO) @DecimalMin(value = "0.01", message = Mensajes.MONTO)
        @Digits(integer = 6, fraction = 2, message = Mensajes.MONTO_DECIMALES) BigDecimal monto,
        @Pattern(regexp = "\\d{6,12}", message = Mensajes.OPERACION) String codigoOperacion,
        @Valid DatosComprobanteSolicitud comprobante,
        Long registradoPor,
        String registradoPorUsuario,
        DatosReserva reserva) {

    public SolicitudCaja cobro() {
        return new SolicitudCaja(codigoReserva, tipoPago, metodo, monto, codigoOperacion, comprobante);
    }
}
