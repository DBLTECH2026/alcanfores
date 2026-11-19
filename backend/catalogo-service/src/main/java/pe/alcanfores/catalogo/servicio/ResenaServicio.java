package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.ResenaAdminRespuesta;
import pe.alcanfores.catalogo.dto.ResenaComplejoSolicitud;
import pe.alcanfores.catalogo.dto.ResenaPublicaRespuesta;
import pe.alcanfores.catalogo.dto.ResenaSolicitud;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.modelo.EstadoResena;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;

import java.util.List;
import java.util.Map;

public interface ResenaServicio {

    /** Reseñas PUBLICADAS de una cancha activa; {@code orden} = recientes | mejor | peor. */
    PaginaRespuesta<ResenaPublicaRespuesta> listarPublicadas(Long idCancha, String orden, int pagina, int tamanio);

    /**
     * Reseña verificada de un cliente con sesión: el DNI sale del token y reservas-service confirma que la reserva es suya,
     * de esa cancha y ya jugada. 422 si no es elegible (incluye código + DNI que no calzan), 409 si la reserva ya tiene
     * reseña (solo después de comprobar código y DNI).
     */
    ResenaPublicaRespuesta crear(Long idCancha, ResenaSolicitud solicitud, UsuarioToken cliente);

    /** Todas las reseñas de la cancha (clientes y complejo, también ocultas) para su ficha en el panel. */
    List<ResenaAdminRespuesta> listarDeCancha(Long idCancha);

    /** Reseña que carga el complejo (sin reserva); la fecha no puede ser futura. */
    ResenaAdminRespuesta crearDelComplejo(Long idCancha, ResenaComplejoSolicitud solicitud);

    /** Solo las del complejo: 422 {@code RESENA_DE_CLIENTE} con una reseña de cliente. */
    ResenaAdminRespuesta editarDelComplejo(Long id, ResenaComplejoSolicitud solicitud);

    /** Solo las del complejo: 422 {@code RESENA_DE_CLIENTE} con una reseña de cliente. */
    void eliminarDelComplejo(Long id);

    /** ¿La reserva ya tiene reseña (publicada u oculta)? Lo usa reservas para la bandera {@code puedeResenar}. */
    boolean existeParaReserva(String codigoReserva);

    /** Promedio (1 decimal), total y distribución 5…1 de las reseñas publicadas. */
    ResumenResenasRespuesta resumen(Long idCancha);

    /** Promedio y total por id de cancha (para las tarjetas del portal). */
    Map<Long, CalificacionRespuesta> calificacionesPorCancha();

    PaginaRespuesta<ResenaAdminRespuesta> listarAdmin(EstadoResena estado, Long idCancha, Integer puntuacion, int pagina,
                                                      int tamanio);

    ResenaAdminRespuesta cambiarEstado(Long id, EstadoResena estado);

    ResenaAdminRespuesta responder(Long id, String respuesta);
}
