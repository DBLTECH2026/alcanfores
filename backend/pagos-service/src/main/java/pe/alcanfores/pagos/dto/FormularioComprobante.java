package pe.alcanfores.pagos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.comun.validacion.Dni;

/**
 * Campos de texto del formulario multipart «subir comprobante Yape/Plin» (la imagen se valida aparte por bytes mágicos).
 */
public record FormularioComprobante(
        @NotNull(message = Mensajes.CODIGO) @CodigoReserva String codigoReserva,
        @NotNull(message = Mensajes.DNI) @Dni String dni,
        @NotNull(message = Mensajes.METODO) @Pattern(regexp = "YAPE|PLIN", message = Mensajes.METODO) String metodo,
        @NotNull(message = Mensajes.OPERACION) @Pattern(regexp = "\\d{6,12}", message = Mensajes.OPERACION) String codigoOperacion,
        @Valid DatosComprobanteSolicitud comprobante) {
}
