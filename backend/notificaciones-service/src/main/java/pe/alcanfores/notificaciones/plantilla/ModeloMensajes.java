package pe.alcanfores.notificaciones.plantilla;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.canal.CorreoCanal;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Convierte los datos de un evento en el modelo plano (textos ya formateados) que usan las plantillas de correo y de
 * WhatsApp. También arma los enlaces del portal con {@code FRONT_URL}.
 */
@Component
public class ModeloMensajes {

    private final AlcanforesPropiedades alcanfores;

    public ModeloMensajes(AlcanforesPropiedades alcanfores) {
        this.alcanfores = alcanfores;
    }

    /** Datos del complejo, enlaces generales y marca (lo que va en la cabecera y el pie de todos los mensajes). */
    public Map<String, Object> base(DatosComplejo c) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombreComplejo", c.nombre());
        m.put("direccion", c.direccion());
        m.put("referencia", c.referencia());
        m.put("mapaUrl", c.mapaUrl());
        m.put("whatsappTexto", c.whatsappTexto());
        m.put("whatsappUrl", c.whatsappUrl());
        m.put("correoComplejo", c.correo());
        m.put("devolucionHoras", c.devolucionHoras());
        m.put("recordatorioHoras", c.recordatorioHoras());
        m.put("urlInicio", frontUrl());
        m.put("urlReservar", frontUrl() + "/reservar");
        m.put("urlPrivacidad", frontUrl() + "/privacidad");
        m.put("logoSrc", "cid:" + CorreoCanal.CID_LOGO);
        m.put("anio", Tiempo.hoy().getYear());
        return m;
    }

    /** Base + datos de la reserva (código, cancha, fecha, hora, montos) y sus enlaces para el cliente. */
    public Map<String, Object> reserva(DatosReserva r, DatosComplejo c) {
        Map<String, Object> m = base(c);
        ClienteContacto cli = r.cliente();
        String nombre = primerNombre(cli);
        m.put("nombre", nombre == null ? "" : nombre);
        m.put("saludo", nombre == null ? "Hola" : "Hola, " + nombre);
        m.put("nombreCompleto", cli == null ? "" : cli.nombreCompleto());
        m.put("codigo", r.codigoReserva());
        m.put("cancha", r.nombreCancha());
        m.put("deporte", deporteTexto(r.deporte()));
        m.put("canchaTexto", canchaTexto(r.nombreCancha(), r.deporte()));
        m.put("fecha", capitalizar(Tiempo.fechaLarga(r.fecha())));
        m.put("fechaTexto", Tiempo.fechaLarga(r.fecha()));
        m.put("fechaCorta", r.fecha().format(Tiempo.FECHA_CORTA));
        m.put("horaInicio", Tiempo.horaAmPm(r.horaInicio()));
        m.put("horario", horario(r));
        BigDecimal total = cero(r.montoTotal());
        BigDecimal pagado = cero(r.montoPagado());
        m.put("montoTotal", Dinero.formato(total));
        m.put("montoAdelanto", Dinero.formato(cero(r.montoAdelanto())));
        m.put("montoPagado", Dinero.formato(pagado));
        m.put("hayPago", pagado.signum() > 0);
        BigDecimal saldo = total.subtract(pagado).max(BigDecimal.ZERO);
        m.put("saldo", Dinero.formato(saldo));
        m.put("haySaldo", saldo.signum() > 0);
        m.put("urlMiReserva", urlMiReserva(r.codigoReserva()));
        m.put("urlListo", frontUrl() + "/reservar/listo/" + r.codigoReserva());
        m.put("urlResena", urlResena(r.idCancha(), r.codigoReserva()));
        return m;
    }

    /**
     * «7:00–8:00 p. m.» o, con varios turnos, «9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)». Usa el {@code horarioTexto}
     * que arma reservas-service; los eventos y recordatorios guardados antes de la v2 no lo traen: entonces se arma con
     * la hora de inicio y fin.
     */
    public static String horario(DatosReserva r) {
        String texto = r.horarioTexto() != null && !r.horarioTexto().isBlank() ? r.horarioTexto().trim()
                : Horarios.rango(r.horaInicio(), r.horaFin());
        return r.cantidadTurnos() > 1 ? texto + " (" + r.cantidadTurnos() + " turnos)" : texto;
    }

    /** Sobrescribe el saldo cuando se conoce uno más actual (evento confirmada o consulta a reservas). */
    public void fijarSaldo(Map<String, Object> m, BigDecimal saldo) {
        BigDecimal s = cero(saldo).max(BigDecimal.ZERO);
        m.put("saldo", Dinero.formato(s));
        m.put("haySaldo", s.signum() > 0);
    }

    public String urlMiReserva(String codigo) {
        return UriComponentsBuilder.fromUriString(frontUrl()).path("/mi-reserva").queryParam("codigo", codigo)
                .build().toUriString();
    }

    public String urlResena(Long idCancha, String codigo) {
        return UriComponentsBuilder.fromUriString(frontUrl()).path("/canchas/{id}").queryParam("resena", codigo)
                .buildAndExpand(idCancha).toUriString();
    }

    /** {@code FRONT_URL} puede ser una lista separada por comas (CORS): los enlaces usan la primera. */
    public String frontUrl() {
        String url = alcanfores.getFrontUrl() == null ? "" : alcanfores.getFrontUrl().split(",")[0].trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** «sábado 21 de noviembre, 7:00 p. m.» */
    public static String fechaHoraTexto(LocalDateTime momento) {
        String fecha = Tiempo.fechaLarga(momento.toLocalDate());
        String sinAnio = fecha.substring(0, fecha.lastIndexOf(" de "));
        return sinAnio + ", " + Tiempo.horaAmPm(momento.toLocalTime());
    }

    /** Plazo legible: si es hoy solo la hora («7:20 p. m.»), si no con la fecha. */
    public static String plazoTexto(LocalDateTime venceEn) {
        LocalDate hoy = Tiempo.hoy();
        LocalTime hora = venceEn.toLocalTime();
        if (venceEn.toLocalDate().equals(hoy)) {
            return Tiempo.horaAmPm(hora) + " de hoy";
        }
        return fechaHoraTexto(venceEn);
    }

    /** «2 horas», «1 hora y 15 minutos», «45 minutos». */
    public static String duracionTexto(long minutos) {
        long h = minutos / 60;
        long min = minutos % 60;
        String horas = h == 1 ? "1 hora" : h + " horas";
        String mins = min == 1 ? "1 minuto" : min + " minutos";
        if (h == 0) {
            return mins;
        }
        return min == 0 ? horas : horas + " y " + mins;
    }

    /** «Cancha 1» + FUTBOL_5 → «Cancha 1 · Fútbol 5»; si el nombre ya trae el deporte, se deja tal cual. */
    public static String canchaTexto(String nombre, String deporte) {
        String d = deporteTexto(deporte);
        if (nombre == null || nombre.isBlank()) {
            return d;
        }
        return d.isEmpty() || nombre.toLowerCase(Tiempo.ES_PE).contains(d.toLowerCase(Tiempo.ES_PE)) ? nombre : nombre + " · " + d;
    }

    public static String deporteTexto(String deporte) {
        if (deporte == null) {
            return "";
        }
        return switch (deporte) {
            case "FUTBOL_5" -> "Fútbol 5";
            case "FUTBOL_6" -> "Fútbol 6";
            case "FUTBOL_7" -> "Fútbol 7";
            case "VOLEY" -> "Vóley";
            case "BASQUET" -> "Básquet";
            case "MULTIUSO" -> "Vóley y básquet";
            default -> deporte;
        };
    }

    private static String primerNombre(ClienteContacto c) {
        if (c == null || c.nombres() == null || c.nombres().isBlank()) {
            return null;
        }
        return c.nombres().trim().split("\\s+")[0];
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
