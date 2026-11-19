package pe.alcanfores.pagos.cliente;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** API REST de Mercado Pago (Checkout Pro). */
public interface MercadoPagoCliente {

    /** {@code POST /checkout/preferences}. */
    PreferenciaMp crearPreferencia(Map<String, Object> cuerpo, String claveIdempotencia);

    /** {@code GET /v1/payments/{id}}; vacío si no existe. */
    Optional<PagoMp> obtenerPago(String idPago);

    /** {@code GET /v1/payments/search?external_reference=…}: pagos de un intento, del más reciente al más antiguo. */
    List<PagoMp> buscarPorReferencia(String referenciaExterna);

    record PreferenciaMp(String id, String initPoint, String sandboxInitPoint) {
    }

    record PagoMp(String id, String estado, String detalleEstado, String referenciaExterna, BigDecimal monto,
                  String medio, String tipoMedio, String codigoAutorizacion, String ultimos4) {

        public boolean aprobado() {
            return "approved".equals(estado);
        }

        public boolean rechazado() {
            return "rejected".equals(estado) || "cancelled".equals(estado);
        }
    }
}
