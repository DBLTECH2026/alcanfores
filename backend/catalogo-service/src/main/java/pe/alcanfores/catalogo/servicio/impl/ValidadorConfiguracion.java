package pe.alcanfores.catalogo.servicio.impl;

import org.springframework.stereotype.Component;
import pe.alcanfores.catalogo.modelo.ClaveConfiguracion;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.validacion.Celular;
import pe.alcanfores.comun.validacion.Documentos;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.DIAS_GENERADOS;
import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.DIAS_VISIBLES;
import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.DURACION_MIN;
import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.INICIO_NOCHE;
import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.PRIMER_TURNO;
import static pe.alcanfores.catalogo.modelo.ClaveConfiguracion.ULTIMO_TURNO;

/**
 * Valida y normaliza cada valor de configuración según su tipo, y luego las reglas entre claves (horario coherente).
 * Los errores se devuelven por clave para que el panel marque el campo exacto.
 */
@Component
public class ValidadorConfiguracion {

    private static final Pattern ENTERO = Pattern.compile("^-?\\d{1,9}$");
    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d{1,3}(\\.\\d{1,8})?$");
    private static final Pattern MINUTOS = Pattern.compile("^\\d{1,4}$");
    static final int MAX_RECORDATORIOS = 3;
    private static final Celular.Validador CELULAR = new Celular.Validador();

    /** Resultado de validar un valor: el valor normalizado o el mensaje de error. */
    public record Resultado(String valor, String error) {
        static Resultado ok(String valor) {
            return new Resultado(valor, null);
        }

        static Resultado error(String mensaje) {
            return new Resultado(null, mensaje);
        }

        public boolean valido() {
            return error == null;
        }
    }

    public Resultado validar(ClaveConfiguracion clave, String valorCrudo) {
        if (valorCrudo == null || valorCrudo.isBlank()) {
            return Resultado.error("Este dato es obligatorio.");
        }
        String v = valorCrudo.trim();
        return switch (clave.tipo()) {
            case PORCENTAJE -> entero(v, clave, "Ingresa un porcentaje entero entre " + clave.minimo() + " y " + clave.maximo() + ".");
            case ENTERO -> entero(v, clave, "Ingresa un número entero entre " + clave.minimo() + " y " + clave.maximo() + ".");
            case LISTA_MINUTOS -> listaMinutos(v, clave);
            case HORA -> HORA.matcher(v).matches() ? Resultado.ok(v)
                    : Resultado.error("Usa el formato de hora HH:mm (por ejemplo, 08:00).");
            case CELULAR -> CELULAR.isValid(v, null) ? Resultado.ok(Texto.normalizarCelular(v))
                    : Resultado.error("Ingresa un celular peruano de 9 dígitos que empiece con 9.");
            case CORREO -> v.length() <= clave.maximo() && CORREO.matcher(v).matches() ? Resultado.ok(Texto.correo(v))
                    : Resultado.error("Ingresa un correo válido (máximo " + clave.maximo() + " caracteres).");
            case URL -> url(v, clave);
            case LATITUD, LONGITUD -> coordenada(v, clave);
            case RUC -> Documentos.rucValido(v) ? Resultado.ok(v) : Resultado.error("El RUC no es válido.");
            case TEXTO -> texto(v, clave);
            case BOOLEANO -> booleano(v);
        };
    }

    /**
     * Reglas entre claves sobre la configuración completa ya combinada (valores actuales + cambios). Solo se revisan
     * si las claves involucradas pasaron su validación individual.
     */
    public List<ErrorCampo> validarConjunto(Map<String, String> c) {
        List<ErrorCampo> errores = new ArrayList<>();
        LocalTime primer = LocalTime.parse(c.get(PRIMER_TURNO.clave()));
        LocalTime ultimo = LocalTime.parse(c.get(ULTIMO_TURNO.clave()));
        LocalTime noche = LocalTime.parse(c.get(INICIO_NOCHE.clave()));
        int duracion = Integer.parseInt(c.get(DURACION_MIN.clave()));

        if (duracion % 15 != 0) {
            errores.add(new ErrorCampo(DURACION_MIN.clave(), "La duración debe ser múltiplo de 15 minutos."));
        }
        if (!ultimo.isAfter(primer)) {
            errores.add(new ErrorCampo(ULTIMO_TURNO.clave(), "La hora de cierre debe ser posterior al primer turno."));
        } else {
            if (Duration.between(primer, ultimo).toMinutes() < duracion) {
                errores.add(new ErrorCampo(DURACION_MIN.clave(),
                        "Entre el primer turno y el cierre no entra ni un turno de esa duración."));
            }
            if (noche.isBefore(primer) || noche.isAfter(ultimo)) {
                errores.add(new ErrorCampo(INICIO_NOCHE.clave(),
                        "La tarifa de noche debe empezar entre el primer turno y la hora de cierre."));
            }
        }
        if (Integer.parseInt(c.get(DIAS_GENERADOS.clave())) < Integer.parseInt(c.get(DIAS_VISIBLES.clave()))) {
            errores.add(new ErrorCampo(DIAS_GENERADOS.clave(),
                    "Los días con turnos creados no pueden ser menos que los días visibles para reservar."));
        }
        return errores;
    }

    /** Interruptor: «true»/«false» (también acepta el booleano JSON y mayúsculas); se guarda en minúsculas. */
    private static Resultado booleano(String v) {
        String n = v.toLowerCase(java.util.Locale.ROOT);
        return n.equals("true") || n.equals("false") ? Resultado.ok(n)
                : Resultado.error("Elige encendido o apagado (true o false).");
    }

    private static Resultado entero(String v, ClaveConfiguracion clave, String mensaje) {
        if (!ENTERO.matcher(v).matches()) {
            return Resultado.error(mensaje);
        }
        int n = Integer.parseInt(v);
        return n < clave.minimo() || n > clave.maximo() ? Resultado.error(mensaje) : Resultado.ok(String.valueOf(n));
    }

    /** «15, 120» → «120,15»: de 1 a {@value #MAX_RECORDATORIOS} enteros dentro del rango, sin repetir, de mayor a menor. */
    private static Resultado listaMinutos(String v, ClaveConfiguracion clave) {
        String mensaje = "Escribe de 1 a " + MAX_RECORDATORIOS + " tiempos en minutos (" + clave.minimo() + " a "
                + clave.maximo() + "), separados por comas y sin repetir.";
        String[] partes = v.split(",", -1);
        if (partes.length > MAX_RECORDATORIOS) {
            return Resultado.error(mensaje);
        }
        TreeSet<Integer> minutos = new TreeSet<>(Comparator.reverseOrder());
        for (String parte : partes) {
            String p = parte.trim();
            if (!MINUTOS.matcher(p).matches()) {
                return Resultado.error(mensaje);
            }
            int n = Integer.parseInt(p);
            if (n < clave.minimo() || n > clave.maximo() || !minutos.add(n)) {
                return Resultado.error(mensaje);
            }
        }
        return Resultado.ok(minutos.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    /** Lista guardada («120,15») → enteros de mayor a menor; los valores ilegibles se ignoran. */
    public static List<Integer> minutos(String lista) {
        List<Integer> r = new ArrayList<>();
        for (String p : (lista == null ? "" : lista).split(",")) {
            if (MINUTOS.matcher(p.trim()).matches()) {
                r.add(Integer.parseInt(p.trim()));
            }
        }
        r.sort(Comparator.reverseOrder());
        return r;
    }

    private static Resultado coordenada(String v, ClaveConfiguracion clave) {
        String nombre = clave.tipo() == ClaveConfiguracion.Tipo.LATITUD ? "La latitud" : "La longitud";
        String mensaje = nombre + " debe ser un número entre " + clave.minimo() + " y " + clave.maximo()
                + " (con punto decimal).";
        if (!DECIMAL.matcher(v).matches()) {
            return Resultado.error(mensaje);
        }
        BigDecimal n = new BigDecimal(v);
        return n.compareTo(BigDecimal.valueOf(clave.minimo())) < 0 || n.compareTo(BigDecimal.valueOf(clave.maximo())) > 0
                ? Resultado.error(mensaje) : Resultado.ok(n.toPlainString());
    }

    private static Resultado url(String v, ClaveConfiguracion clave) {
        String mensaje = "Ingresa un enlace que empiece con https:// (máximo " + clave.maximo() + " caracteres).";
        if (v.length() > clave.maximo() || !v.startsWith("https://") || v.chars().anyMatch(Character::isWhitespace)) {
            return Resultado.error(mensaje);
        }
        try {
            URI uri = URI.create(v);
            return uri.getHost() == null || !uri.getHost().contains(".") ? Resultado.error(mensaje) : Resultado.ok(v);
        } catch (IllegalArgumentException e) {
            return Resultado.error(mensaje);
        }
    }

    private static Resultado texto(String v, ClaveConfiguracion clave) {
        String limpio = Texto.limpiar(v);
        String mensaje = "Escribe entre " + clave.minimo() + " y " + clave.maximo() + " caracteres.";
        if (limpio.length() < clave.minimo() || limpio.length() > clave.maximo()) {
            return Resultado.error(mensaje);
        }
        if (limpio.chars().anyMatch(ch -> ch == '<' || ch == '>' || Character.isISOControl(ch))) {
            return Resultado.error("No uses los caracteres < ni >.");
        }
        return Resultado.ok(limpio);
    }
}
