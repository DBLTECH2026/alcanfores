package pe.alcanfores.pagos.modelo;

/** Medios de pago: los cuatro primeros los usa la web; EFECTIVO y TARJETA_POS solo la caja (además de YAPE/PLIN). */
public enum MetodoPago {
    YAPE("Yape"), PLIN("Plin"), TARJETA("Tarjeta"), MERCADO_PAGO("Mercado Pago"), EFECTIVO("Efectivo"),
    TARJETA_POS("Tarjeta (POS)");

    private final String texto;

    MetodoPago(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }
}
