package pe.alcanfores.notificaciones.plantilla;

import pe.alcanfores.comun.eventos.Eventos;

import java.util.Arrays;
import java.util.List;

/**
 * Cada aviso al cliente: el evento que lo origina (se guarda en {@code notificacion.evento}), el nombre de su plantilla
 * ({@code plantillas/correo/<plantilla>.html} y {@code plantillas/whatsapp/<plantilla>.txt}), un texto para el panel y
 * su tarjeta ilustrada ({@code whatsapp/tarjetas/<tarjeta>.jpg}): imagen del WhatsApp y cabecera del correo.
 */
public enum TipoAviso {

    RESERVA_REGISTRADA(Eventos.RESERVA_REGISTRADA, "reserva-registrada", "Reserva registrada", "reserva-registrada"),
    PAGO_REGISTRADO(Eventos.PAGO_REGISTRADO, "pago-registrado", "Comprobante recibido", "comprobante-recibido"),
    PAGO_RECHAZADO(Eventos.PAGO_RECHAZADO, "pago-rechazado", "Pago observado", "comprobante-observado"),
    RESERVA_CONFIRMADA(Eventos.RESERVA_CONFIRMADA, "reserva-confirmada", "Reserva confirmada", "reserva-confirmada"),
    SALDO_PAGADO(Eventos.PAGO_VALIDADO, "saldo-pagado", "Saldo pagado", "saldo-pagado"),
    COMPROBANTE_EMITIDO(Eventos.COMPROBANTE_EMITIDO, "comprobante-emitido", "Comprobante electrónico", "comprobante-emitido"),
    RESERVA_CANCELADA(Eventos.RESERVA_CANCELADA, "reserva-cancelada", "Reserva cancelada", "reserva-cancelada"),
    RESERVA_REPROGRAMADA(Eventos.RESERVA_REPROGRAMADA, "reserva-reprogramada", "Cambio de horario", "reserva-reprogramada"),
    INVITACION_RESENA(Eventos.RESERVA_ASISTENCIA, "invitacion-resena", "Invitación a calificar", "asistencia-confirmada"),
    /** La tarjeta depende del deporte de la cancha: ver {@link #tarjeta(String)}. */
    RECORDATORIO("recordatorio", "recordatorio", "Recordatorio del partido", "recordatorio-futbol"),
    CODIGO_VERIFICACION("cuenta.codigo", "codigo-verificacion", "Código para cambiar la contraseña", "codigo-verificacion"),
    CONTRASENA_CAMBIADA("cuenta.contrasena", "contrasena-cambiada", "Contraseña cambiada", "contrasena-cambiada"),
    PRUEBA_WHATSAPP("whatsapp.prueba", null, "Prueba de WhatsApp", null),
    /** Solo correo: se redacta con {@link RedactorMensajes#redactarCorreo}. */
    PRUEBA_CORREO("correo.prueba", "prueba-correo", "Prueba de correo", null);

    private final String evento;
    private final String plantilla;
    private final String texto;
    private final String tarjeta;

    TipoAviso(String evento, String plantilla, String texto, String tarjeta) {
        this.evento = evento;
        this.plantilla = plantilla;
        this.texto = texto;
        this.tarjeta = tarjeta;
    }

    public String evento() {
        return evento;
    }

    public String plantilla() {
        return plantilla;
    }

    public String texto() {
        return texto;
    }

    /**
     * Tarjeta ilustrada del aviso (sin extensión) o {@code null} si no lleva. El recordatorio usa la del deporte:
     * FUTBOL_* → futbol, VOLEY y MULTIUSO → voley, BASQUET → basquet.
     */
    public String tarjeta(String deporte) {
        if (this != RECORDATORIO || deporte == null) {
            return tarjeta;
        }
        if (deporte.startsWith("FUTBOL")) {
            return "recordatorio-futbol";
        }
        return switch (deporte) {
            case "VOLEY", "MULTIUSO" -> "recordatorio-voley";
            case "BASQUET" -> "recordatorio-basquet";
            default -> tarjeta;
        };
    }

    /** Avisos que la tarea de reintentos no reenvía sola: las pruebas del panel y los códigos (se guardan enmascarados). */
    public static List<String> sinReintentoAutomatico() {
        return List.of(PRUEBA_WHATSAPP.evento, PRUEBA_CORREO.evento, CODIGO_VERIFICACION.evento);
    }

    /** Un código guardado como «******» no se puede reenviar: el cliente pide uno nuevo. */
    public static boolean reenviable(String evento) {
        return !CODIGO_VERIFICACION.evento.equals(evento);
    }

    /** Texto legible para el panel a partir del valor guardado en {@code notificacion.evento}. */
    public static String textoDe(String evento) {
        return Arrays.stream(values()).filter(t -> t.evento.equals(evento)).findFirst().map(TipoAviso::texto).orElse(evento);
    }
}
