package pe.alcanfores.comun.seguridad;

/**
 * Datos de la persona autenticada que viajan dentro del JWT: un administrador (ADMIN / RECEPCION) o un cliente con cuenta
 * (CLIENTE). {@code dni} solo lo tienen los clientes (es el vínculo con sus reservas); para administradores es nulo.
 */
public record UsuarioToken(Long id, String usuario, String nombre, String rol, String dni) {

    public static final String ADMIN = "ADMIN";
    public static final String RECEPCION = "RECEPCION";
    public static final String CLIENTE = "CLIENTE";

    /** Administrador (sin DNI). */
    public UsuarioToken(Long id, String usuario, String nombre, String rol) {
        this(id, usuario, nombre, rol, null);
    }

    public boolean esAdmin() {
        return ADMIN.equals(rol);
    }

    public boolean esCliente() {
        return CLIENTE.equals(rol);
    }
}
