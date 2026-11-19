package pe.alcanfores.auth.servicio;

import org.springframework.stereotype.Component;
import pe.alcanfores.auth.config.AuthPropiedades;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta en memoria los intentos fallidos con usuarios que NO existen y los «bloquea» igual que a uno real. Así la
 * respuesta 423 no delata qué cuentas existen (enumeración de usuarios). Se limpia sola y tiene tope de tamaño.
 */
@Component
public class RegistroIntentosAnonimos {

    static final int MAXIMO_ENTRADAS = 10_000;

    private final Map<String, Intentos> registro = new ConcurrentHashMap<>();
    private final int maximo;
    private final int minutosBloqueo;

    public RegistroIntentosAnonimos(AuthPropiedades propiedades) {
        this.maximo = propiedades.intentosMaximos();
        this.minutosBloqueo = propiedades.minutosBloqueo();
    }

    /** Fin del bloqueo si el usuario (inexistente) está bloqueado en este momento. */
    public Optional<LocalDateTime> bloqueadoHasta(String usuario, LocalDateTime ahora) {
        Intentos i = registro.get(usuario);
        return i != null && i.bloqueadoHasta() != null && i.bloqueadoHasta().isAfter(ahora)
                ? Optional.of(i.bloqueadoHasta()) : Optional.empty();
    }

    /** @return el fin del bloqueo si este fallo completó el máximo de intentos */
    public Optional<LocalDateTime> registrarFallo(String usuario, LocalDateTime ahora) {
        if (registro.size() >= MAXIMO_ENTRADAS) {
            limpiar(ahora);
            if (registro.size() >= MAXIMO_ENTRADAS) {
                return Optional.empty();
            }
        }
        Intentos nuevo = registro.compute(usuario, (k, previo) -> {
            int cuenta = previo == null || previo.bloqueadoHasta() != null ? 1 : previo.cuenta() + 1;
            return cuenta >= maximo
                    ? new Intentos(0, ahora.plusMinutes(minutosBloqueo), ahora)
                    : new Intentos(cuenta, null, ahora);
        });
        return Optional.ofNullable(nuevo.bloqueadoHasta());
    }

    /** Quita las entradas cuyo bloqueo ya pasó o que llevan inactivas más que el tiempo de bloqueo. */
    void limpiar(LocalDateTime ahora) {
        LocalDateTime limite = ahora.minusMinutes(minutosBloqueo);
        registro.entrySet().removeIf(e -> e.getValue().ultimoIntento().isBefore(limite)
                && (e.getValue().bloqueadoHasta() == null || !e.getValue().bloqueadoHasta().isAfter(ahora)));
    }

    int tamanio() {
        return registro.size();
    }

    private record Intentos(int cuenta, LocalDateTime bloqueadoHasta, LocalDateTime ultimoIntento) {
    }
}
