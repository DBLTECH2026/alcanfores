package pe.alcanfores.comun.seguridad;

/** Datos del administrador autenticado que viajan dentro del JWT. */
public record UsuarioToken(Long id, String usuario, String nombre, String rol) {

    public static final String ADMIN = "ADMIN";
    public static final String RECEPCION = "RECEPCION";

    public boolean esAdmin() {
        return ADMIN.equals(rol);
    }
}
