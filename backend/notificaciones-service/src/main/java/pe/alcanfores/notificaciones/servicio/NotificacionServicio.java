package pe.alcanfores.notificaciones.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.dto.NotificacionResumen;

/** Bandeja de envíos del panel y reintentos (manuales y automáticos). */
public interface NotificacionServicio {

    PaginaRespuesta<NotificacionResumen> listar(FiltroNotificaciones filtro, Pageable pagina);

    NotificacionDetalle detalle(Long id);

    /** Reenvío manual (ADMIN) de un aviso FALLIDO, aunque ya haya agotado los intentos automáticos. */
    NotificacionDetalle reintentar(Long id);

    /** Tarea cada 5 min: FALLIDOS con {@code intentos < 3} y PENDIENTES colgados. @return cuántos se reintentaron. */
    int reintentarFallidas();
}
