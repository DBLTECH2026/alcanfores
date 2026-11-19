package pe.alcanfores.comun.eventos;

/** Datos de contacto del cliente que viajan en los eventos. */
public record ClienteContacto(String dni, String nombres, String apellidos, String telefono, String correo) {

    public String nombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }
}
