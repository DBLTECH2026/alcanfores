package pe.alcanfores.pagos.modelo;

/** Tipos de comprobante (catálogo 01 de SUNAT): 03 boleta, 01 factura, 07 nota de crédito. */
public enum TipoComprobante {
    BOLETA("03", "Boleta de venta electrónica"),
    FACTURA("01", "Factura electrónica"),
    NOTA_CREDITO("07", "Nota de crédito electrónica");

    private final String codigo;
    private final String nombre;

    TipoComprobante(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }
}
