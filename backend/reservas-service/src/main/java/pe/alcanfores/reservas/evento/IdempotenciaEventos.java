package pe.alcanfores.reservas.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.modelo.EventoProcesado;
import pe.alcanfores.reservas.repositorio.EventoProcesadoRepositorio;

import java.util.UUID;

/** Consumo idempotente: cada idEvento se aplica una sola vez (tabla evento_procesado). */
@Component
@RequiredArgsConstructor
public class IdempotenciaEventos {

    private final EventoProcesadoRepositorio repositorio;

    /**
     * Marca el evento como procesado dentro de la transacción del consumidor.
     *
     * @return {@code true} si es la primera vez (hay que aplicarlo); {@code false} si ya se había aplicado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean primeraVez(UUID idEvento, String tipo) {
        String id = idEvento.toString();
        if (repositorio.existsById(id)) {
            return false;
        }
        repositorio.save(new EventoProcesado(id, tipo, Tiempo.ahora()));
        return true;
    }
}
