package pe.alcanfores.catalogo.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PublicadorEventos;

/**
 * Envía {@code cancha.actualizada} a RabbitMQ SOLO cuando la transacción que cambió la cancha se confirmó: si el
 * guardado se revierte, reservas-service nunca se entera de un precio o estado que no existe.
 */
@Component
@RequiredArgsConstructor
public class PublicadorCanchaActualizada {

    private final PublicadorEventos publicador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmar(CanchaActualizadaEvento evento) {
        publicador.publicar(Eventos.CANCHA_ACTUALIZADA, evento);
    }
}
