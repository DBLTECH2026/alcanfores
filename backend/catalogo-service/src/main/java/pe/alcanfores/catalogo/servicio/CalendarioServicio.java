package pe.alcanfores.catalogo.servicio;

import pe.alcanfores.catalogo.dto.CalendarioDtos.CargaFeriadosRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialRespuesta;
import pe.alcanfores.catalogo.dto.CalendarioDtos.DiaEspecialSolicitud;

import java.time.LocalDate;
import java.util.List;

/** Calendario de feriados y días no laborables; cada cambio publica {@code calendario.actualizado}. */
public interface CalendarioServicio {

    /** Días especiales del año ({@code null} = año actual), ordenados por fecha. */
    List<DiaEspecialRespuesta> delAnio(Integer anio);

    /** Días especiales entre dos fechas (portal e interno); por defecto hoy … hoy + 60, máximo 366 días. */
    List<DiaEspecialRespuesta> entre(LocalDate desde, LocalDate hasta);

    /** 409 {@code DIA_ESPECIAL_DUPLICADO} si la fecha ya está; 409 {@code DIA_CON_RESERVAS} si cerraría canchas con reservas. */
    DiaEspecialRespuesta crear(DiaEspecialSolicitud solicitud);

    DiaEspecialRespuesta actualizar(Long id, DiaEspecialSolicitud solicitud);

    void eliminar(Long id);

    /** Carga los feriados nacionales de Perú del año sin duplicar (una fecha que ya tiene día especial se respeta). */
    CargaFeriadosRespuesta cargarFeriadosPeru(Integer anio);
}
