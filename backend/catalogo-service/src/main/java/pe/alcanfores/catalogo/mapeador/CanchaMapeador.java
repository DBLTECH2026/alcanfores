package pe.alcanfores.catalogo.mapeador;

import org.springframework.stereotype.Component;
import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaInternaRespuesta;
import pe.alcanfores.catalogo.dto.CanchaResumenRespuesta;
import pe.alcanfores.catalogo.dto.FotoRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.DiaRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.FeriadoRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioCanchaRespuesta;
import pe.alcanfores.catalogo.dto.HorarioDtos.HorarioRespuesta;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.CanchaFoto;
import pe.alcanfores.catalogo.modelo.HorarioCancha;
import pe.alcanfores.catalogo.util.Horas;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.HorarioDia;
import pe.alcanfores.comun.util.Tiempo;

import java.util.List;
import java.util.UUID;

/** Convierte entidades de cancha y foto a las respuestas del contrato (SPEC §5.2). */
@Component
public class CanchaMapeador {

    /** Ruta pública de las fotos (el gateway la enruta a este servicio). */
    public static final String RUTA_FOTOS = "/api/catalogo/fotos/";

    public String url(String archivo) {
        return archivo == null ? null : RUTA_FOTOS + archivo;
    }

    public FotoRespuesta foto(CanchaFoto f) {
        return new FotoRespuesta(f.getId(), url(f.getArchivo()), f.getLeyenda(), f.isPrincipal(), f.getOrden(),
                f.getAncho(), f.getAlto());
    }

    public CanchaResumenRespuesta resumen(Cancha c, String archivoPrincipal, CalificacionRespuesta calificacion) {
        return new CanchaResumenRespuesta(c.getId(), c.getSlug(), c.getNombre(), c.getDeporte().name(),
                c.getDeporte().getTexto(), c.getSuperficie(), c.getCapacidad(), c.isTechada(), c.isIluminacionLed(),
                c.getDescripcion(), c.getPrecioDia(), c.getPrecioNoche(), url(archivoPrincipal), calificacion);
    }

    public CanchaDetalleRespuesta detalle(Cancha c, List<CanchaFoto> fotos, ResumenResenasRespuesta resumen) {
        String principal = fotos.stream().filter(CanchaFoto::isPrincipal).map(CanchaFoto::getArchivo).findFirst()
                .orElse(fotos.isEmpty() ? null : fotos.get(0).getArchivo());
        return new CanchaDetalleRespuesta(c.getId(), c.getSlug(), c.getNombre(), c.getDeporte().name(),
                c.getDeporte().getTexto(), c.getSuperficie(), c.getCapacidad(), c.isTechada(), c.isIluminacionLed(),
                c.getDescripcion(), c.getDescripcionLarga(), c.getMedidas(),
                c.getCaracteristicas() == null ? List.of() : List.copyOf(c.getCaracteristicas()),
                c.getPrecioDia(), c.getPrecioNoche(), url(principal), fotos.stream().map(this::foto).toList(),
                new CalificacionRespuesta(resumen.promedio(), resumen.total()), resumen,
                c.getEstado().name(), c.getOrden(), c.getCreadoEn(), c.getActualizadoEn(), horario(c), feriado(c));
    }

    public HorarioRespuesta horario(Cancha c) {
        return new HorarioRespuesta(c.getDuracionTurnoMin(), Horas.formato(c.getInicioNoche()), dias(c),
                Horas.resumen(c.getHorario()));
    }

    public FeriadoRespuesta feriado(Cancha c) {
        return new FeriadoRespuesta(c.getFeriadoModo().name(), c.getRecargoFeriado());
    }

    /** {@code GET/PUT /api/admin/canchas/{id}/horario}. */
    public HorarioCanchaRespuesta horarioCompleto(Cancha c) {
        HorarioRespuesta h = horario(c);
        return new HorarioCanchaRespuesta(h.duracionTurnoMin(), h.inicioNoche(), h.dias(), h.texto(), feriado(c));
    }

    /** cancha.actualizada con el horario completo (lo consume reservas-service para regenerar los turnos). */
    public CanchaActualizadaEvento evento(Cancha c) {
        return new CanchaActualizadaEvento(UUID.randomUUID(), Tiempo.ahora(), c.getId(), c.getNombre(), c.getDeporte().name(),
                c.getPrecioDia(), c.getPrecioNoche(), c.getEstado().name(), c.getDuracionTurnoMin(), c.getInicioNoche(),
                semana(c), c.getFeriadoModo().name(), c.getRecargoFeriado());
    }

    private static List<DiaRespuesta> dias(Cancha c) {
        return c.getHorario().stream()
                .map(h -> new DiaRespuesta(h.getDiaSemana(), h.isAbierto(), Horas.formato(h.getApertura()),
                        Horas.formato(h.getCierre())))
                .toList();
    }

    private static List<HorarioDia> semana(Cancha c) {
        return c.getHorario().stream().map(CanchaMapeador::dia).toList();
    }

    private static HorarioDia dia(HorarioCancha h) {
        return new HorarioDia(h.getDiaSemana(), h.isAbierto(), h.getApertura(), h.getCierre());
    }

    public CanchaInternaRespuesta interna(Cancha c) {
        return new CanchaInternaRespuesta(c.getId(), c.getSlug(), c.getNombre(), c.getDeporte().name(),
                c.getDeporte().getTexto(), c.getCapacidad(), c.getPrecioDia(), c.getPrecioNoche(), c.getEstado().name(),
                c.getOrden(), c.getDuracionTurnoMin(), c.getInicioNoche(), semana(c), c.getFeriadoModo().name(),
                c.getRecargoFeriado());
    }
}
