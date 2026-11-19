package pe.alcanfores.catalogo.evento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PublicadorEventos;

/** Envía {@code calendario.actualizado} a RabbitMQ solo si la transacción que cambió el calendario se confirmó. */
@Component
@RequiredArgsConstructor
public class PublicadorCalendarioActualizado {

    private final PublicadorEventos publicador;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmar(CalendarioActualizadoEvento evento) {
        publicador.publicar(Eventos.CALENDARIO_ACTUALIZADO, evento);
    }
}
