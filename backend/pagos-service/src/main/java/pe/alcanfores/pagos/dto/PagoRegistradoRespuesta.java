package pe.alcanfores.pagos.dto;

/** Respuesta 201 al subir el comprobante de Yape/Plin. */
public record PagoRegistradoRespuesta(Long idPago, String estado, String mensaje) {
}
