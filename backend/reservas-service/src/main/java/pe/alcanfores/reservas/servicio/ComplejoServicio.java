package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;

import java.util.List;
import java.util.Optional;

/**
 * Copia en memoria de la configuración y las canchas de catalogo-service. Se refresca cada 5 minutos y al consumir
 * {@code cancha.actualizada}; si catálogo no responde, se conservan los últimos valores (o los del SPEC al arrancar).
 */
public interface ComplejoServicio {

    ParametrosComplejo parametros();

    /** Todas las canchas conocidas (activas e inactivas), ordenadas por id. */
    List<CanchaCatalogo> canchas();

    Optional<CanchaCatalogo> cancha(Long idCancha);

    /**
     * Vuelve a leer catálogo.
     *
     * @return las canchas nuevas o con cambios (nombre, deporte, precios o estado) respecto de la copia anterior
     */
    List<CanchaCatalogo> refrescar();

    /** Actualiza una cancha en la copia local (evento {@code cancha.actualizada}). */
    void actualizarCancha(CanchaCatalogo cancha);
}
