package pe.alcanfores.reservas.dto;

/** Mensajes de validación del SPEC §3 (los mismos que muestra el front con Zod). */
public final class Mensajes {

    public static final String DNI = "El DNI debe tener 8 dígitos.";
    public static final String RUC = "El RUC no es válido.";
    public static final String FACTURA = "Para factura necesitas un RUC válido y la razón social.";
    public static final String NOMBRE = "Usa solo letras (2 a 60).";
    public static final String CELULAR = "Ingresa un celular peruano de 9 dígitos que empiece con 9.";
    public static final String CORREO = "Ingresa un correo válido; ahí te enviamos la copia de tu reserva.";
    public static final String TURNO = "Ese turno ya no está disponible.";
    public static final String ELEGIR_TURNO = "Elige un turno.";
    public static final String METODO_PAGO = "Elige un método de pago.";
    public static final String CODIGO_OPERACION = "El número de operación tiene de 6 a 12 dígitos.";
    public static final String MOTIVO = "Cuéntanos el motivo (mínimo 10 caracteres).";
    public static final String CODIGO_RESERVA = "El código tiene el formato ALC-XXXXXX.";
    public static final String TERMINOS = "Debes aceptar los términos y la política de privacidad.";
    public static final String DATOS_CLIENTE = "Completa los datos del cliente.";
    public static final String ORIGEN = "Indica si la reserva es presencial o telefónica.";
    public static final String NOTA = "La nota admite hasta 255 caracteres.";
    public static final String TIPO_PAGO = "Indica si el pago es adelanto o total.";
    public static final String MONTO = "Ingresa un monto mayor a cero con hasta 2 decimales.";
    public static final String TIPO_COMPROBANTE = "Elige boleta o factura.";
    public static final String ASISTENCIA = "Indica si el cliente asistió o no.";
    public static final String MOTIVO_BLOQUEO = "Indica el motivo del bloqueo (3 a 120 caracteres).";
    public static final String FECHA = "Elige una fecha válida.";
    public static final String HORA = "Elige una hora válida.";
    public static final String CANCHA = "Elige una cancha.";
    public static final String TURNOS_CANTIDAD = "Elige entre 1 y 8 turnos.";
    public static final String TURNOS_REPETIDOS = "No repitas el mismo turno.";
    public static final String TURNOS_MISMO_DIA = "Los turnos deben ser de la misma cancha y del mismo día.";
    public static final String ESTADO = "Elige un estado válido: PENDIENTE, CONFIRMADA o CANCELADA.";
    public static final String MOTIVO_REPROGRAMAR = "Cuéntanos el motivo del cambio (5 a 200 caracteres, sin enlaces ni todo en mayúsculas).";
    /** Máximo de turnos por reserva. */
    public static final int MAX_TURNOS = 8;

    private Mensajes() {
    }

    /** «Elige una fecha entre hoy y los próximos 14 días.» */
    public static String rangoFecha(int diasVisibles) {
        return "Elige una fecha entre hoy y los próximos " + diasVisibles + " días.";
    }
}
