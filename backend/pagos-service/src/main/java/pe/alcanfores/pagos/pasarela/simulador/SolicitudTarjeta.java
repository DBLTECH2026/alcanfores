package pe.alcanfores.pagos.pasarela.simulador;

/**
 * Datos de tarjeta que envía el checkout. Viajan solo en memoria: nunca se guardan ni se registran en logs
 * (se conserva únicamente la marca y los últimos 4 dígitos). {@link #toString()} enmascara el número y oculta el CVV.
 */
public record SolicitudTarjeta(String numero, String titular, Integer mesVencimiento, Integer anioVencimiento,
                               String cvv, String correo) {

    /** Solo dígitos (se aceptan espacios o guiones al escribir). */
    public String numeroLimpio() {
        return numero == null ? "" : numero.replaceAll("[\\s-]", "");
    }

    public String ultimos4() {
        String n = numeroLimpio();
        return n.length() >= 4 ? n.substring(n.length() - 4) : n;
    }

    /** «**** **** **** 1111» para logs y mensajes. */
    public String enmascarado() {
        return "**** **** **** " + ultimos4();
    }

    @Override
    public String toString() {
        return "SolicitudTarjeta[numero=" + enmascarado() + ", titular=" + titular + ", vencimiento="
                + mesVencimiento + "/" + anioVencimiento + ", cvv=***, correo=" + correo + "]";
    }
}
