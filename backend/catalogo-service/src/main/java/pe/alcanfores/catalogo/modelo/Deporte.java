package pe.alcanfores.catalogo.modelo;

/** Deportes que se juegan en el complejo, con el texto que ve el cliente. */
public enum Deporte {
    FUTBOL_5("Fútbol 5"),
    FUTBOL_6("Fútbol 6"),
    FUTBOL_7("Fútbol 7"),
    VOLEY("Vóley"),
    BASQUET("Básquet"),
    MULTIUSO("Multiusos (vóley y básquet)");

    private final String texto;

    Deporte(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
