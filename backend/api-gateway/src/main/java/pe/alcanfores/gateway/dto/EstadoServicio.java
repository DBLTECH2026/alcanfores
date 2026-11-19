package pe.alcanfores.gateway.dto;

/**
 * Salud de un microservicio.
 *
 * @param nombre     nombre de Eureka ({@code spring.application.name})
 * @param estado     {@code UP} si al menos una instancia responde {@code /actuator/health} con UP; si no, {@code DOWN}
 * @param instancias instancias registradas (0 = no está en Eureka)
 * @param latenciaMs tiempo de respuesta de la instancia más rápida que respondió UP ({@code null} si ninguna)
 */
public record EstadoServicio(String nombre, String estado, int instancias, Long latenciaMs) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    public static EstadoServicio caido(String nombre, int instancias) {
        return new EstadoServicio(nombre, DOWN, instancias, null);
    }
}
