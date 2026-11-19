package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.ResenaAdminRespuesta;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaSolicitud;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.util.Map;

public interface ResenaServicio {

    /** Reseñas PUBLICADAS de una cancha activa; {@code orden} = recientes | mejor | peor. */
    PaginaRespuesta<ResenaPublicaRespuesta> listarPublicadas(Long idCancha, String orden, int pagina, int tamanio);

    /** Reseña verificada con reservas-service: 422 si no es elegible, 409 si la reserva ya tiene reseña. */
    ResenaPublicaRespuesta crear(Long idCancha, ResenaSolicitud solicitud);

    /** Promedio (1 decimal), total y distribución 5…1 de las reseñas publicadas. */
    ResumenResenasRespuesta resumen(Long idCancha);

    /** Promedio y total por id de cancha (para las tarjetas del portal). */
    Map<Long, CalificacionRespuesta> calificacionesPorCancha();

    PaginaRespuesta<ResenaAdminRespuesta> listarAdmin(EstadoResena estado, Long idCancha, Integer puntuacion, int pagina,
                                                      int tamanio);

    ResenaAdminRespuesta cambiarEstado(Long id, EstadoResena estado);

    ResenaAdminRespuesta responder(Long id, String respuesta);
}
