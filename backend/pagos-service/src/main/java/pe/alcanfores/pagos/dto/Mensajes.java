package pe.alcanfores.pagos.dto;

/** Mensajes de validación del SPEC §3 (los mismos que muestra el front con Zod). */
public final class Mensajes {

    public static final String CODIGO = "El código tiene el formato ALC-XXXXXX.";
    public static final String DNI = "El DNI debe tener 8 dígitos.";
    public static final String METODO = "Elige un método de pago.";
    public static final String OPERACION = "El número de operación tiene de 6 a 12 dígitos.";
    public static final String IMAGEN = "Sube una imagen JPG o PNG de hasta 5 MB.";
    public static final String IMAGEN_PEQUENA = "La imagen es muy pequeña: debe medir al menos 200 px por lado.";
    public static final String MOTIVO = "Cuéntanos el motivo (mínimo 10 caracteres).";
    public static final String TIPO_PAGO = "Elige si pagas el adelanto o el total.";
    public static final String TIPO_PAGO_CAJA = "Elige el tipo de pago: adelanto, total o saldo.";
    public static final String MONTO = "Ingresa un monto mayor que cero.";
    public static final String MONTO_DECIMALES = "El monto admite hasta 2 decimales.";
    public static final String CELULAR = "Ingresa un celular peruano de 9 dígitos que empiece con 9.";
    public static final String CODIGO_APROBACION = "El código de aprobación tiene 6 dígitos.";
    public static final String CODIGO_3DS = "El código de verificación tiene 6 dígitos.";

    private Mensajes() {
    }
}
