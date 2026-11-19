package pe.alcanfores.reservas.cliente;

import java.math.BigDecimal;

/** Llamadas síncronas a pagos-service. */
public interface PagosCliente {

    /**
     * {@code POST /interno/pagos/caja}: registra un cobro presencial ya validado (y su comprobante) para una reserva
     * manual. pagos-service publica luego {@code pago.validado}, que confirma la reserva.
     *
     * @throws pe.alcanfores.comun.error.NegocioExcepcion si pagos rechaza el cobro o no responde
     */
    void registrarPagoCaja(PagoCaja pago);

    record PagoCaja(String codigoReserva, String tipoPago, String metodo, BigDecimal monto, String codigoOperacion,
                    Comprobante comprobante, Long registradoPor, String registradoPorUsuario) {
    }

    record Comprobante(String tipo, String ruc, String razonSocial, String direccionFiscal) {
    }
}
