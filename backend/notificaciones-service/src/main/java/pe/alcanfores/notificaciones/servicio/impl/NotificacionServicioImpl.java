package pe.alcanfores.notificaciones.servicio.impl;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.dto.NotificacionResumen;
import pe.alcanfores.notificaciones.mapeador.NotificacionMapeador;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;
import pe.alcanfores.notificaciones.servicio.FiltroNotificaciones;
import pe.alcanfores.notificaciones.servicio.NotificacionServicio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class NotificacionServicioImpl implements NotificacionServicio {

    private static final Logger log = LoggerFactory.getLogger(NotificacionServicioImpl.class);
    /** Un aviso PENDIENTE más antiguo que esto quedó colgado (el proceso se cayó durante el envío). */
    static final int MINUTOS_COLGADO = 5;

    private final NotificacionRepositorio notificaciones;
    private final EnvioServicio envio;
    private final NotificacionMapeador mapeador;
    private final NotificacionesPropiedades propiedades;
    private final Auditor auditor;

    public NotificacionServicioImpl(NotificacionRepositorio notificaciones, EnvioServicio envio,
                                    NotificacionMapeador mapeador, NotificacionesPropiedades propiedades, Auditor auditor) {
        this.notificaciones = notificaciones;
        this.envio = envio;
        this.mapeador = mapeador;
        this.propiedades = propiedades;
        this.auditor = auditor;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<NotificacionResumen> listar(FiltroNotificaciones f, Pageable pagina) {
        return PaginaRespuesta.de(notificaciones.findAll(especificacion(f), pagina), mapeador::aResumen);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionDetalle detalle(Long id) {
        return mapeador.aDetalle(buscar(id));
    }

    @Override
    public NotificacionDetalle reintentar(Long id) {
        Notificacion n = buscar(id);
        if (n.getEstado() != EstadoNotificacion.FALLIDO) {
            throw NegocioExcepcion.regla("NO_REINTENTABLE",
                    "Solo se pueden reintentar los avisos fallidos; este está " + n.getEstado().name() + ".");
        }
        if (notificaciones.tomarParaEnvio(id, EstadoNotificacion.FALLIDO) == 0) {
            throw NegocioExcepcion.conflicto("NOTIFICACION_EN_PROCESO", "Ese aviso se está reenviando en este momento.");
        }
        Notificacion resultado = envio.enviar(buscar(id));
        auditor.registrar("REINTENTAR_NOTIFICACION", "notificacion", id, resultado.getCanal() + " a "
                + resultado.getDestino() + " · " + resultado.getEvento() + " · resultado " + resultado.getEstado());
        return mapeador.aDetalle(resultado);
    }

    @Override
    public int reintentarFallidas() {
        List<Long> ids = notificaciones.idsPorReintentar(propiedades.getMaxIntentos(),
                Tiempo.ahora().minusMinutes(MINUTOS_COLGADO));
        int reintentados = 0;
        for (Long id : ids) {
            Notificacion n = notificaciones.findById(id).orElse(null);
            if (n == null || notificaciones.tomarParaEnvio(id, n.getEstado()) == 0) {
                continue;
            }
            envio.enviar(notificaciones.findById(id).orElseThrow());
            reintentados++;
        }
        if (reintentados > 0) {
            log.info("Reintentos automáticos: {} avisos reenviados", reintentados);
        }
        return reintentados;
    }

    private Notificacion buscar(Long id) {
        return notificaciones.findById(id)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos la notificación " + id + "."));
    }

    static Specification<Notificacion> especificacion(FiltroNotificaciones f) {
        return (raiz, consulta, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.canal() != null) {
                p.add(cb.equal(raiz.get("canal"), f.canal()));
            }
            if (f.estado() != null) {
                p.add(cb.equal(raiz.get("estado"), f.estado()));
            }
            if (f.evento() != null && !f.evento().isBlank()) {
                p.add(cb.equal(raiz.get("evento"), f.evento().trim()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String patron = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                p.add(cb.or(cb.like(cb.lower(raiz.get("codigoReserva")), patron),
                        cb.like(cb.lower(raiz.get("destino")), patron),
                        cb.like(cb.lower(raiz.get("asunto")), patron)));
            }
            if (f.desde() != null) {
                p.add(cb.greaterThanOrEqualTo(raiz.get("creadoEn"), f.desde().atStartOfDay()));
            }
            if (f.hasta() != null) {
                p.add(cb.lessThan(raiz.get("creadoEn"), f.hasta().plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
