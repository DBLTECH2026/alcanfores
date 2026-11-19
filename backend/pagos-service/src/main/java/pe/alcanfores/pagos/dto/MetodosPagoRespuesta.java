package pe.alcanfores.pagos.dto;

import java.util.List;

/** {@code GET /api/pagos/metodos}: medios disponibles y datos de prueba de la pasarela beta. */
public record MetodosPagoRespuesta(Yape yape, Plin plin, boolean tarjetaBeta, boolean mercadoPago,
                                   List<TarjetaPrueba> tarjetasPrueba, YapePrueba yapePrueba, String codigo3dsPrueba,
                                   int adelantoPct) {

    public record Yape(String numero, String titular) {
    }

    public record Plin(String numero) {
    }

    public record TarjetaPrueba(String numero, String marca, String resultado) {
    }

    public record YapePrueba(String codigoAprueba, String codigoRechaza) {
    }
}
