package pe.alcanfores.pagos.pasarela.simulador;

import java.util.Optional;

/** Marcas reconocidas por el número (BIN): Visa 4, Mastercard 51–55 / 2221–2720, Amex 34/37, Diners 36/38. */
public enum MarcaTarjeta {
    VISA("Visa", 3), MASTERCARD("Mastercard", 3), AMEX("American Express", 4), DINERS("Diners Club", 3);

    private final String nombre;
    private final int digitosCvv;

    MarcaTarjeta(String nombre, int digitosCvv) {
        this.nombre = nombre;
        this.digitosCvv = digitosCvv;
    }

    public String nombre() {
        return nombre;
    }

    public int digitosCvv() {
        return digitosCvv;
    }

    public static Optional<MarcaTarjeta> detectar(String numero) {
        if (numero == null || numero.length() < 2) {
            return Optional.empty();
        }
        int dos = Integer.parseInt(numero.substring(0, 2));
        if (numero.charAt(0) == '4') {
            return Optional.of(VISA);
        }
        if (dos >= 51 && dos <= 55) {
            return Optional.of(MASTERCARD);
        }
        if (numero.length() >= 4) {
            int cuatro = Integer.parseInt(numero.substring(0, 4));
            if (cuatro >= 2221 && cuatro <= 2720) {
                return Optional.of(MASTERCARD);
            }
        }
        if (dos == 34 || dos == 37) {
            return Optional.of(AMEX);
        }
        if (dos == 36 || dos == 38) {
            return Optional.of(DINERS);
        }
        return Optional.empty();
    }
}
