package pe.alcanfores.comun.validacion;

import java.util.regex.Pattern;

/** Reglas de los documentos peruanos (DNI y RUC de SUNAT). */
public final class Documentos {

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern RUC = Pattern.compile("^(10|15|16|17|20)\\d{9}$");
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private Documentos() {
    }

    public static boolean dniValido(String dni) {
        return dni != null && DNI.matcher(dni).matches();
    }

    public static boolean celularValido(String celularNormalizado) {
        return celularNormalizado != null && CELULAR.matcher(celularNormalizado).matches();
    }

    /** RUC de 11 dígitos con prefijo válido y dígito verificador módulo 11 (algoritmo de SUNAT). */
    public static boolean rucValido(String ruc) {
        if (ruc == null || !RUC.matcher(ruc).matches()) {
            return false;
        }
        return Character.getNumericValue(ruc.charAt(10)) == digitoVerificadorRuc(ruc.substring(0, 10));
    }

    /** Dígito verificador de los 10 primeros dígitos de un RUC. */
    public static int digitoVerificadorRuc(String diezDigitos) {
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += Character.getNumericValue(diezDigitos.charAt(i)) * PESOS_RUC[i];
        }
        int d = 11 - (suma % 11);
        return d == 10 ? 0 : d == 11 ? 1 : d;
    }

    /** Persona natural con negocio (10) o persona jurídica (20): las que pueden recibir factura en este sistema. */
    public static boolean rucParaFactura(String ruc) {
        return rucValido(ruc) && (ruc.startsWith("10") || ruc.startsWith("20"));
    }

    /** «70123321» → «7****321» (vistas públicas, Ley 29733). */
    public static String enmascararDni(String dni) {
        if (dni == null || dni.length() < 4) {
            return dni;
        }
        return dni.charAt(0) + "*".repeat(dni.length() - 4) + dni.substring(dni.length() - 3);
    }
}
