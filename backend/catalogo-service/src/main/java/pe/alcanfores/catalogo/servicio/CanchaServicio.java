package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaInternaRespuesta;
import pe.alcanfores.catalogo.dto.CanchaResumenRespuesta;
import pe.alcanfores.catalogo.dto.CanchaSolicitud;
import pe.alcanfores.catalogo.modelo.EstadoCancha;

import java.util.List;

public interface CanchaServicio {

    /** Canchas ACTIVAS para el portal, en el orden configurado. */
    List<CanchaResumenRespuesta> listarActivas();

    /** Detalle público por id numérico o por slug; 404 si no existe o está INACTIVA. */
    CanchaDetalleRespuesta detallePublico(String idOSlug);

    /** Todas las canchas (también las inactivas) para el panel. */
    List<CanchaDetalleRespuesta> listarTodas();

    CanchaDetalleRespuesta detalle(Long id);

    CanchaDetalleRespuesta crear(CanchaSolicitud solicitud);

    CanchaDetalleRespuesta actualizar(Long id, CanchaSolicitud solicitud);

    CanchaDetalleRespuesta cambiarEstado(Long id, EstadoCancha estado);

    List<CanchaInternaRespuesta> listarInternas();

    CanchaInternaRespuesta interna(Long id);
}
