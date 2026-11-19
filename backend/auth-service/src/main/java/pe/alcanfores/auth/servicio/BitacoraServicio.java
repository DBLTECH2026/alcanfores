package pe.alcanfores.auth.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.auth.dto.BitacoraRespuesta;
import pe.alcanfores.auth.dto.FiltroBitacora;
import pe.alcanfores.auth.dto.OpcionesBitacora;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.util.PaginaRespuesta;

/** Bitácora central: guarda las acciones propias de auth-service y las que llegan de los demás servicios por RabbitMQ. */
public interface BitacoraServicio {

    /** Acción hecha por el administrador autenticado en esta solicitud (o «Sistema» si no hay sesión). */
    void registrar(String accion, String entidad, Object idEntidad, String detalle);

    /** Acción con el actor indicado a mano (por ejemplo, un intento de login que aún no tiene sesión). */
    void registrarComo(Long idActor, String actor, String rol, String accion, String entidad, Object idEntidad, String detalle);

    /**
     * Guarda un evento {@code auditoria.accion} una sola vez.
     *
     * @return {@code true} si se guardó; {@code false} si ya se había procesado (reentrega de RabbitMQ)
     */
    boolean registrarEvento(AccionAuditadaEvento evento);

    PaginaRespuesta<BitacoraRespuesta> buscar(FiltroBitacora filtro, Pageable pagina);

    OpcionesBitacora opciones();
}
