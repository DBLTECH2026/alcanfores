package pe.alcanfores.auth.modelo;

/** Un usuario INACTIVO no puede iniciar sesión, pero se conserva para la bitácora. */
public enum EstadoUsuario {
    ACTIVO,
    INACTIVO
}
