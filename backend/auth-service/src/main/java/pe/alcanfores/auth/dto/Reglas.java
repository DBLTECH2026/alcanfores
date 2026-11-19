package pe.alcanfores.auth.dto;

/** Expresiones y mensajes de validación compartidos por los formularios de usuarios (SPEC §3). */
public final class Reglas {

    public static final String USUARIO = "^[a-z][a-z0-9._]{3,29}$";
    public static final String MENSAJE_USUARIO = "Usuario de 4 a 30 caracteres en minúsculas.";
    /** Correo con dominio que tenga al menos un punto (ej. nombre@alcanfores.pe). */
    public static final String CORREO = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$";
    public static final String MENSAJE_CORREO = "Ingresa un correo válido de hasta 100 caracteres.";

    private Reglas() {
    }
}
