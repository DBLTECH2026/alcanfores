package pe.alcanfores.notificaciones.cliente;

/** Error al hablar con Evolution API, con un mensaje en español listo para la bitácora o el panel. */
public class EvolutionExcepcion extends RuntimeException {

    private final int status;

    public EvolutionExcepcion(int status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public EvolutionExcepcion(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = 0;
    }

    /** Código HTTP que devolvió Evolution (0 si no hubo respuesta: conexión rechazada, timeout…). */
    public int getStatus() {
        return status;
    }
}
