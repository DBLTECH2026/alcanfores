package pe.alcanfores.notificaciones.mapeador;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pe.alcanfores.notificaciones.canal.CorreoCanal;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.dto.NotificacionResumen;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

/** Entidad → DTO del panel. Para la vista previa del correo cambia el logo {@code cid:} por una data URI. */
@Component
public class NotificacionMapeador {

    private final String logoDataUri;

    public NotificacionMapeador() {
        try (InputStream in = new ClassPathResource(CorreoCanal.RUTA_LOGO).getInputStream()) {
            this.logoDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró el logo del correo en " + CorreoCanal.RUTA_LOGO, e);
        }
    }

    public NotificacionResumen aResumen(Notificacion n) {
        return new NotificacionResumen(n.getId(), n.getCodigoReserva(), n.getEvento(), TipoAviso.textoDe(n.getEvento()),
                n.getCanal().name(), n.getDestino(), n.getAsunto(), n.getEstado().name(), n.getIntentos(),
                n.getUltimoError(), n.getAdjuntos().size(), n.getCreadoEn(), n.getEnviadoEn());
    }

    /** {@code reintentable}: el admin puede reenviar a mano cualquier aviso FALLIDO (aunque ya agotó los automáticos). */
    public NotificacionDetalle aDetalle(Notificacion n) {
        boolean correo = n.getCanal() == CanalNotificacion.CORREO;
        return new NotificacionDetalle(n.getId(), n.getCodigoReserva(), n.getEvento(), TipoAviso.textoDe(n.getEvento()),
                n.getCanal().name(), n.getDestino(), n.getAsunto(), n.getEstado().name(), n.getIntentos(),
                n.getUltimoError(), n.getCreadoEn(), n.getEnviadoEn(), correo ? "HTML" : "TEXTO", n.getCuerpo(),
                correo ? vistaPrevia(n.getCuerpo()) : null, n.getAdjuntos(), n.getEstado() == EstadoNotificacion.FALLIDO);
    }

    /** HTML listo para verse fuera de un cliente de correo (logo incrustado). */
    public String vistaPrevia(String html) {
        return html.replace("cid:" + CorreoCanal.CID_LOGO, logoDataUri);
    }
}
