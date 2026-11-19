package pe.alcanfores.pagos.pasarela.simulador;

/** Respuesta de la pasarela beta al checkout: APROBADO, RECHAZADO o REQUIERE_3DS. */
public record ResultadoPasarela(String estado, String codigoAutorizacion, String motivo, String marca, String ultimos4) {

    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String REQUIERE_3DS = "REQUIERE_3DS";
}
