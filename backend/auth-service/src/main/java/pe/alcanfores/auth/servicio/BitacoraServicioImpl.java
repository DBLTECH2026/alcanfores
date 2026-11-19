package pe.alcanfores.auth.servicio;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.auth.dto.BitacoraRespuesta;
import pe.alcanfores.auth.dto.FiltroBitacora;
import pe.alcanfores.auth.dto.OpcionesBitacora;
import pe.alcanfores.auth.mapeador.BitacoraMapeador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.EventoProcesado;
import pe.alcanfores.auth.repositorio.BitacoraRepositorio;
import pe.alcanfores.auth.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Tiempo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BitacoraServicioImpl implements BitacoraServicio {

    private static final Logger log = LoggerFactory.getLogger(BitacoraServicioImpl.class);
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fecha", "servicio", "actor", "accion", "entidad");
    private static final Sort ORDEN_DEFECTO = Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "id"));

    private final BitacoraRepositorio bitacoras;
    private final EventoProcesadoRepositorio eventos;
    private final String servicio;

    public BitacoraServicioImpl(BitacoraRepositorio bitacoras, EventoProcesadoRepositorio eventos,
                                @Value("${spring.application.name:auth-service}") String servicio) {
        this.bitacoras = bitacoras;
        this.eventos = eventos;
        this.servicio = servicio;
    }

    @Override
    @Transactional
    public void registrar(String accion, String entidad, Object idEntidad, String detalle) {
        UsuarioToken u = SeguridadUtil.actual().orElse(null);
        registrarComo(u == null ? null : u.id(), u == null ? "Sistema" : u.usuario(), u == null ? "SISTEMA" : u.rol(),
                accion, entidad, idEntidad, detalle);
    }

    @Override
    @Transactional
    public void registrarComo(Long idActor, String actor, String rol, String accion, String entidad, Object idEntidad,
                              String detalle) {
        bitacoras.save(BitacoraMapeador.nueva(servicio, idActor, actor, rol, accion, entidad,
                idEntidad == null ? null : String.valueOf(idEntidad), detalle, IpCliente.actual(), Tiempo.ahora()));
    }

    @Override
    @Transactional
    public boolean registrarEvento(AccionAuditadaEvento evento) {
        String id = evento.idEvento().toString();
        if (eventos.existsById(id)) {
            log.info("Evento de auditoría {} ya procesado: se ignora la reentrega", id);
            return false;
        }
        // Si dos consumidores lo reciben a la vez, la PK de evento_procesado hace fallar a uno y su transacción se revierte.
        eventos.saveAndFlush(new EventoProcesado(id, Eventos.AUDITORIA_ACCION, Tiempo.ahora()));
        bitacoras.save(BitacoraMapeador.desdeEvento(evento));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<BitacoraRespuesta> buscar(FiltroBitacora f, Pageable pagina) {
        if (f.desde() != null && f.hasta() != null && f.hasta().isBefore(f.desde())) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        Pageable p = Paginacion.sanear(pagina, ORDEN_PERMITIDO, ORDEN_DEFECTO);
        return PaginaRespuesta.de(bitacoras.findAll(especificacion(f), p), BitacoraMapeador::aRespuesta);
    }

    @Override
    @Transactional(readOnly = true)
    public OpcionesBitacora opciones() {
        return new OpcionesBitacora(bitacoras.servicios(), bitacoras.acciones());
    }

    private static Specification<Bitacora> especificacion(FiltroBitacora f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tiene(f.servicio())) {
                p.add(cb.equal(root.get("servicio"), f.servicio().trim()));
            }
            if (tiene(f.accion())) {
                p.add(cb.equal(root.get("accion"), f.accion().trim().toUpperCase(Locale.ROOT)));
            }
            if (tiene(f.entidad())) {
                p.add(cb.equal(root.get("entidad"), f.entidad().trim()));
            }
            if (tiene(f.actor())) {
                p.add(cb.like(cb.lower(root.get("actor")), Paginacion.patronContiene(f.actor()), '\\'));
            }
            if (f.desde() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("fecha"), f.desde().atStartOfDay()));
            }
            if (f.hasta() != null) {
                p.add(cb.lessThan(root.get("fecha"), f.hasta().plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static boolean tiene(String s) {
        return s != null && !s.isBlank();
    }
}
