package pe.alcanfores.auth.dto;

/** Expresiones y mensajes de validación compartidos por los formularios de usuarios (SPEC §3). */
public final class Reglas {

    public static final String USUARIO = "^[a-z][a-z0-9._]{3,29}$";
    public static final String MENSAJE_USUARIO = "Usuario de 4 a 30 caracteres en minúsculas.";
    /** Correo con dominio que tenga al menos un punto (ej. nombre@alcanfores.pe). */
    public static final String CORREO = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$";
    public static final String MENSAJE_CORREO = "Ingresa un correo válido de hasta 100 caracteres.";

    // Cuentas de clientes: los mismos mensajes que el formulario de reserva (SPEC §3).
    public static final String MENSAJE_DNI = "El DNI debe tener 8 dígitos.";
    public static final String MENSAJE_NOMBRE = "Usa solo letras (2 a 60).";
    public static final String MENSAJE_CELULAR = "Ingresa un celular peruano de 9 dígitos que empiece con 9.";
    public static final String MENSAJE_CORREO_CLIENTE = "Ingresa un correo válido; ahí te enviamos la copia de tu reserva.";
    public static final String MENSAJE_TERMINOS = "Debes aceptar los términos y la política de privacidad.";

    private Reglas() {
    }

    /** Celular opcional del panel: vacío o solo espacios = sin celular (así el formulario puede borrarlo). */
    public static String telefonoOpcional(String telefono) {
        return telefono == null || telefono.isBlank() ? null : telefono;
    }
}
