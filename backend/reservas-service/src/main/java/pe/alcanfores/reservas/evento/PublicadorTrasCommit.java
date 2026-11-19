package pe.alcanfores.reservas.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.alcanfores.comun.eventos.PublicadorEventos;

/**
 * Publica en RabbitMQ solo DESPUÉS del commit: si la transacción se revierte, el evento no sale (no se anuncia una
 * reserva que no existe). Fuera de una transacción se publica de inmediato.
 */
@Component
@RequiredArgsConstructor
public class PublicadorTrasCommit {

    private final PublicadorEventos publicador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publicar(EventoParaPublicar e) {
        publicador.publicar(e.routingKey(), e.evento());
    }
}
