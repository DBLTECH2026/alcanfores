package pe.alcanfores.notificaciones.plantilla;

import pe.alcanfores.comun.eventos.Eventos;

import java.util.Arrays;

/**
 * Cada aviso al cliente: el evento que lo origina (se guarda en {@code notificacion.evento}), el nombre de su plantilla
 * ({@code plantillas/correo/<plantilla>.html} y {@code plantillas/whatsapp/<plantilla>.txt}) y un texto para el panel.
 */
public enum TipoAviso {

    RESERVA_REGISTRADA(Eventos.RESERVA_REGISTRADA, "reserva-registrada", "Reserva registrada"),
    PAGO_REGISTRADO(Eventos.PAGO_REGISTRADO, "pago-registrado", "Comprobante recibido"),
    PAGO_RECHAZADO(Eventos.PAGO_RECHAZADO, "pago-rechazado", "Pago observado"),
    RESERVA_CONFIRMADA(Eventos.RESERVA_CONFIRMADA, "reserva-confirmada", "Reserva confirmada"),
    COMPROBANTE_EMITIDO(Eventos.COMPROBANTE_EMITIDO, "comprobante-emitido", "Comprobante electrónico"),
    RESERVA_CANCELADA(Eventos.RESERVA_CANCELADA, "reserva-cancelada", "Reserva cancelada"),
    INVITACION_RESENA(Eventos.RESERVA_ASISTENCIA, "invitacion-resena", "Invitación a calificar"),
    RECORDATORIO("recordatorio", "recordatorio", "Recordatorio del partido"),
    PRUEBA_WHATSAPP("whatsapp.prueba", null, "Prueba de WhatsApp");

    private final String evento;
    private final String plantilla;
    private final String texto;

    TipoAviso(String evento, String plantilla, String texto) {
        this.evento = evento;
        this.plantilla = plantilla;
        this.texto = texto;
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

    /** Texto legible para el panel a partir del valor guardado en {@code notificacion.evento}. */
    public static String textoDe(String evento) {
        return Arrays.stream(values()).filter(t -> t.evento.equals(evento)).findFirst().map(TipoAviso::texto).orElse(evento);
    }
}
