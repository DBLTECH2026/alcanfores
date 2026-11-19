package pe.alcanfores.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros propios de auth-service ({@code alcanfores.auth.*}).
 *
 * @param bcryptFuerza    costo de BCrypt (12 en producción; las pruebas lo bajan para ir rápido)
 * @param intentosMaximos intentos fallidos seguidos antes de bloquear la cuenta
 * @param minutosBloqueo  duración del bloqueo
 * @param semilla         claves de los usuarios iniciales (solo por variables de entorno)
 */
@ConfigurationProperties(prefix = "alcanfores.auth")
public record AuthPropiedades(int bcryptFuerza, int intentosMaximos, int minutosBloqueo, Semilla semilla) {

    public AuthPropiedades {
        bcryptFuerza = bcryptFuerza <= 0 ? 12 : bcryptFuerza;
        intentosMaximos = intentosMaximos <= 0 ? 5 : intentosMaximos;
        minutosBloqueo = minutosBloqueo <= 0 ? 15 : minutosBloqueo;
        semilla = semilla == null ? new Semilla(true, "Alcanfores2026", "Recepcion2026", "UTP2026") : semilla;
    }

    public record Semilla(boolean habilitada, String claveAdmin, String claveRecepcion, String claveDocente) {
    }
}
