package pe.alcanfores.pagos.evento;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.EventoDominio;

/**
 * Punto único para anunciar eventos de dominio y acciones auditables. No publica de inmediato: deja un evento de
 * aplicación que {@link PublicadorTrasCommit} envía a RabbitMQ solo si la transacción confirma.
 */
@Component
public class BusEventos {

    private final ApplicationEventPublisher aplicacion;

    public BusEventos(ApplicationEventPublisher aplicacion) {
        this.aplicacion = aplicacion;
    }

    public void publicar(String routingKey, EventoDominio evento) {
        aplicacion.publishEvent(new EventoPendiente(routingKey, evento));
    }

    /** Acción administrativa del usuario autenticado (bitácora central). */
    public void auditar(String accion, String entidad, Object idEntidad, String detalle) {
        aplicacion.publishEvent(new AuditoriaPendiente(null, null, accion, entidad, idEntidad, detalle));
    }

    /** Acción hecha por el sistema u otro servicio (sin sesión): se indica el actor. */
    public void auditarComo(String actor, String rol, String accion, String entidad, Object idEntidad, String detalle) {
        aplicacion.publishEvent(new AuditoriaPendiente(actor, rol, accion, entidad, idEntidad, detalle));
    }

    public record EventoPendiente(String routingKey, EventoDominio evento) {
    }

    public record AuditoriaPendiente(String actor, String rol, String accion, String entidad, Object idEntidad,
                                     String detalle) {
    }
}
