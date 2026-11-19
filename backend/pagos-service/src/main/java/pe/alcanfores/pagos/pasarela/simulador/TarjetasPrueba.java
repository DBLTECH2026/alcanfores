package pe.alcanfores.pagos.pasarela.simulador;

import java.util.List;
import java.util.Map;

/** Tarjetas de prueba de la pasarela beta (SPEC §5.4) y su resultado. */
public final class TarjetasPrueba {

    public enum Resultado {
        APROBADA(null),
        REQUIERE_3DS(null),
        FONDOS_INSUFICIENTES("Fondos insuficientes. Prueba con otra tarjeta."),
        BLOQUEADA("La tarjeta está bloqueada. Comunícate con tu banco."),
        VENCIDA("La tarjeta está vencida."),
        NO_RECONOCIDA("En modo beta solo se aceptan las tarjetas de prueba.");

        private final String motivo;

        Resultado(String motivo) {
            this.motivo = motivo;
        }

        public String motivo() {
            return motivo;
        }
    }

    public record Tarjeta(String numero, String marca, String resultado) {
    }

    public static final String CODIGO_3DS = "123456";
    public static final String YAPE_APRUEBA = "123456";
    public static final String YAPE_RECHAZA = "000000";

    private static final Map<String, Resultado> RESULTADOS = Map.of(
            "4111111111111111", Resultado.APROBADA,
            "5555555555554444", Resultado.APROBADA,
            "378282246310005", Resultado.APROBADA,
            "4000002760003184", Resultado.REQUIERE_3DS,
            "4000000000000002", Resultado.FONDOS_INSUFICIENTES,
            "4000000000009995", Resultado.BLOQUEADA,
            "4000000000000069", Resultado.VENCIDA);

    private static final List<Tarjeta> LISTA = List.of(
            new Tarjeta("4111 1111 1111 1111", "Visa", "Aprobada"),
            new Tarjeta("5555 5555 5555 4444", "Mastercard", "Aprobada"),
            new Tarjeta("3782 822463 10005", "American Express", "Aprobada"),
            new Tarjeta("4000 0027 6000 3184", "Visa", "Pide 3-D Secure (código 123456)"),
            new Tarjeta("4000 0000 0000 0002", "Visa", "Rechazada: fondos insuficientes"),
            new Tarjeta("4000 0000 0000 9995", "Visa", "Rechazada: tarjeta bloqueada"),
            new Tarjeta("4000 0000 0000 0069", "Visa", "Rechazada: tarjeta vencida"));

    private TarjetasPrueba() {
    }

    public static Resultado resultado(String numeroLimpio) {
        return RESULTADOS.getOrDefault(numeroLimpio, Resultado.NO_RECONOCIDA);
    }

    public static List<Tarjeta> lista() {
        return LISTA;
    }
}
