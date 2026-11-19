package pe.alcanfores.comun.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Montos en soles: redondeo, IGV incluido (18 %) e importe en letras para comprobantes. */
public final class Dinero {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal FACTOR_IGV = BigDecimal.ONE.add(TASA_IGV);

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE",
            "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private Dinero() {
    }

    public static BigDecimal redondear(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    /** Valor de venta sin IGV de un precio que ya incluye IGV (operación gravada). */
    public static BigDecimal baseImponible(BigDecimal totalConIgv) {
        return totalConIgv.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
    }

    /** IGV contenido en un precio que ya lo incluye: total − base (así base + IGV cuadra al céntimo). */
    public static BigDecimal igvIncluido(BigDecimal totalConIgv) {
        return redondear(totalConIgv).subtract(baseImponible(totalConIgv));
    }

    /** Porcentaje de un monto, redondeado a 2 decimales (adelanto = total × pct / 100). */
    public static BigDecimal porcentaje(BigDecimal monto, int pct) {
        return monto.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** «S/ 80.00» */
    public static String formato(BigDecimal monto) {
        return "S/ " + redondear(monto).toPlainString();
    }

    /** Importe en letras como en los comprobantes electrónicos: «OCHENTA CON 00/100 SOLES». */
    public static String enLetras(BigDecimal monto) {
        BigDecimal m = redondear(monto);
        long entero = m.longValue();
        int centimos = m.subtract(BigDecimal.valueOf(entero)).movePointRight(2).intValue();
        String letras = entero == 0 ? "CERO" : numeroEnLetras(entero);
        return String.format("%s CON %02d/100 SOLES", letras, centimos);
    }

    static String numeroEnLetras(long n) {
        if (n >= 1_000_000) {
            long millones = n / 1_000_000;
            long resto = n % 1_000_000;
            String prefijo = millones == 1 ? "UN MILLON" : apocopar(numeroEnLetras(millones)) + " MILLONES";
            return resto == 0 ? prefijo : prefijo + " " + numeroEnLetras(resto);
        }
        if (n >= 1000) {
            long miles = n / 1000;
            long resto = n % 1000;
            String prefijo = miles == 1 ? "MIL" : apocopar(menorQueMil((int) miles)) + " MIL";
            return resto == 0 ? prefijo : prefijo + " " + menorQueMil((int) resto);
        }
        return menorQueMil((int) n);
    }

    private static String menorQueMil(int n) {
        if (n == 100) {
            return "CIEN";
        }
        int c = n / 100;
        int resto = n % 100;
        StringBuilder sb = new StringBuilder(CENTENAS[c]);
        if (resto > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (resto < 30) {
                sb.append(UNIDADES[resto]);
            } else {
                sb.append(DECENAS[resto / 10]);
                if (resto % 10 > 0) {
                    sb.append(" Y ").append(UNIDADES[resto % 10]);
                }
            }
        }
        return sb.toString();
    }

    /** «VEINTIUNO MIL» → «VEINTIUN MIL»; «UNO» → «UN». */
    private static String apocopar(String texto) {
        if (texto.endsWith("VEINTIUNO")) {
            return texto.substring(0, texto.length() - 1);
        }
        if (texto.endsWith("UNO")) {
            return texto.substring(0, texto.length() - 1);
        }
        return texto;
    }
}
