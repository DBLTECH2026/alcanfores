package pe.alcanfores.catalogo.mapeador;

import org.springframework.stereotype.Component;
import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaInternaRespuesta;
import pe.alcanfores.catalogo.dto.CanchaResumenRespuesta;
import pe.alcanfores.catalogo.dto.FotoRespuesta;
import pe.alcanfores.catalogo.dto.ResumenResenasRespuesta;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.CanchaFoto;

import java.util.List;

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
                c.getEstado().name(), c.getOrden(), c.getCreadoEn(), c.getActualizadoEn());
    }

    public CanchaInternaRespuesta interna(Cancha c) {
        return new CanchaInternaRespuesta(c.getId(), c.getSlug(), c.getNombre(), c.getDeporte().name(),
                c.getDeporte().getTexto(), c.getCapacidad(), c.getPrecioDia(), c.getPrecioNoche(), c.getEstado().name(),
                c.getOrden());
    }
}
