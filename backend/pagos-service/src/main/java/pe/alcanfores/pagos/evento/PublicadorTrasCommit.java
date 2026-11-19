package pe.alcanfores.pagos.evento;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.eventos.PublicadorEventos;

/** Envía a RabbitMQ los eventos y la auditoría DESPUÉS del commit (nunca se anuncia algo que luego se revierte). */
@Component
public class PublicadorTrasCommit {

    private final PublicadorEventos publicador;
    private final Auditor auditor;

    public PublicadorTrasCommit(PublicadorEventos publicador, Auditor auditor) {
        this.publicador = publicador;
        this.auditor = auditor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmar(BusEventos.EventoPendiente e) {
        publicador.publicar(e.routingKey(), e.evento());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmar(BusEventos.AuditoriaPendiente a) {
        if (a.actor() == null) {
            auditor.registrar(a.accion(), a.entidad(), a.idEntidad(), a.detalle());
        } else {
            auditor.registrarComo(a.actor(), a.rol(), a.accion(), a.entidad(), a.idEntidad(), a.detalle());
        }
    }
}
