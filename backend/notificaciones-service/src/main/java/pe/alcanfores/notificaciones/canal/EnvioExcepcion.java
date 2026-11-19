package pe.alcanfores.notificaciones.canal;

/** El canal no pudo entregar el aviso (el mensaje queda FALLIDO con este texto en {@code ultimo_error}). */
public class EnvioExcepcion extends RuntimeException {

    public EnvioExcepcion(String mensaje) {
        super(mensaje);
    }

    public EnvioExcepcion(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
