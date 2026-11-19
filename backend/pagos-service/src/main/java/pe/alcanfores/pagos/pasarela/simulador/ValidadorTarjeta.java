package pe.alcanfores.pagos.pasarela.simulador;

import pe.alcanfores.comun.error.ErrorCampo;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Validaciones de la tarjeta (SPEC §3): Luhn, 13–19 dígitos, marca, vencimiento futuro, CVV y titular. */
public final class ValidadorTarjeta {

    static final String MSJ_NUMERO = "Revisa el número de tarjeta.";
    static final String MSJ_MARCA = "Aceptamos Visa, Mastercard, American Express y Diners Club.";
    static final String MSJ_VENCIMIENTO = "La fecha de vencimiento no es válida o la tarjeta ya venció.";
    static final String MSJ_CVV_3 = "El CVV tiene 3 dígitos.";
    static final String MSJ_CVV_4 = "El CVV de American Express tiene 4 dígitos.";
    static final String MSJ_TITULAR = "Escribe el nombre del titular como figura en la tarjeta (3 a 60 letras).";
    static final String MSJ_CORREO = "Ingresa un correo válido para enviarte la constancia del pago.";

    private static final Pattern DIGITOS = Pattern.compile("\\d{13,19}");
    private static final Pattern TITULAR = Pattern.compile("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' .-]{3,60}$");
    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private ValidadorTarjeta() {
    }

    /** Algoritmo de Luhn (módulo 10). */
    public static boolean luhn(String digitos) {
        if (digitos == null || digitos.isEmpty() || !digitos.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int suma = 0;
        boolean duplicar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (duplicar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** Año de 2 dígitos («26») o 4 («2026»). */
    static int anioCompleto(int anio) {
        return anio < 100 ? 2000 + anio : anio;
    }

    public static List<ErrorCampo> validar(SolicitudTarjeta t, YearMonth mesActual) {
        List<ErrorCampo> errores = new ArrayList<>();
        String numero = t.numeroLimpio();
        MarcaTarjeta marca = null;
        if (!DIGITOS.matcher(numero).matches() || !luhn(numero)) {
            errores.add(new ErrorCampo("numero", MSJ_NUMERO));
        } else {
            marca = MarcaTarjeta.detectar(numero).orElse(null);
            if (marca == null) {
                errores.add(new ErrorCampo("numero", MSJ_MARCA));
            }
        }
        Integer mes = t.mesVencimiento();
        Integer anio = t.anioVencimiento();
        if (mes == null || anio == null || mes < 1 || mes > 12 || anio < 0
                || YearMonth.of(anioCompleto(anio), mes).isBefore(mesActual)
                || YearMonth.of(anioCompleto(anio), mes).isAfter(mesActual.plusYears(20))) {
            errores.add(new ErrorCampo("vencimiento", MSJ_VENCIMIENTO));
        }
        int digitosCvv = marca == null ? 3 : marca.digitosCvv();
        String cvv = t.cvv() == null ? "" : t.cvv().trim();
        boolean cvvValido = marca == null ? cvv.matches("\\d{3,4}") : cvv.matches("\\d{" + digitosCvv + "}");
        if (!cvvValido) {
            errores.add(new ErrorCampo("cvv", digitosCvv == 4 ? MSJ_CVV_4 : MSJ_CVV_3));
        }
        String titular = t.titular() == null ? "" : t.titular().trim().replaceAll("\\s+", " ");
        if (!TITULAR.matcher(titular).matches() || titular.chars().filter(Character::isLetter).count() < 3) {
            errores.add(new ErrorCampo("titular", MSJ_TITULAR));
        }
        if (t.correo() == null || t.correo().length() > 100 || !CORREO.matcher(t.correo().trim()).matches()) {
            errores.add(new ErrorCampo("correo", MSJ_CORREO));
        }
        return errores;
    }
}
