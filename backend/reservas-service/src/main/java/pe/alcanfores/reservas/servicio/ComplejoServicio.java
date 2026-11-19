package pe.alcanfores.reservas.servicio;

import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Copia en memoria de la configuración, las canchas (con su horario) y el calendario de catalogo-service. Se refresca cada 5 minutos y al consumir
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

    /** Feriado o día no laborable de esa fecha (si hay). */
    Optional<DiaEspecial> diaEspecial(LocalDate fecha);

    /**
     * Vuelve a leer el calendario (hoy … hoy + días generados). Si catálogo no responde se conserva el actual.
     *
     * @return las fechas cuyo día especial apareció, cambió o desapareció
     */
    List<LocalDate> refrescarCalendario();

    /**
     * ¿Ya se leyeron de catálogo las canchas y el calendario al menos una vez? Mientras no, la copia es la semilla del SPEC
     * y no se debe cambiar ni borrar ningún turno existente (solo crear los que falten).
     */
    boolean catalogoLeido();

    /** Aplica a la copia local un día especial nuevo, editado ({@code dia}) o borrado ({@code dia} nulo). */
    void actualizarDia(LocalDate fecha, DiaEspecial dia);
}
