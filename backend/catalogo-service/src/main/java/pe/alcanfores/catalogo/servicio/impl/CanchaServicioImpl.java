package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.dto.CalificacionRespuesta;
import pe.alcanfores.catalogo.dto.CanchaDetalleRespuesta;
import pe.alcanfores.catalogo.dto.CanchaInternaRespuesta;
import pe.alcanfores.catalogo.dto.CanchaResumenRespuesta;
import pe.alcanfores.catalogo.dto.CanchaSolicitud;
import pe.alcanfores.catalogo.mapeador.CanchaMapeador;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.CanchaFoto;
import pe.alcanfores.catalogo.modelo.EstadoCancha;
import pe.alcanfores.catalogo.repositorio.CanchaFotoRepositorio;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.modelo.ClaveConfiguracion;
import pe.alcanfores.catalogo.servicio.CanchaServicio;
import pe.alcanfores.catalogo.servicio.ConfiguracionServicio;
import pe.alcanfores.catalogo.servicio.ResenaServicio;
import pe.alcanfores.catalogo.util.Calificaciones;
import pe.alcanfores.catalogo.util.Slugs;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CanchaServicioImpl implements CanchaServicio {

    static final String NO_ENCONTRADA = "No encontramos esa cancha.";

    private final CanchaRepositorio canchas;
    private final CanchaFotoRepositorio fotos;
    private final ResenaServicio resenas;
    private final ConfiguracionServicio configuracion;
    private final CanchaMapeador mapeador;
    private final ApplicationEventPublisher eventos;
    private final Auditor auditor;

    @Override
    @Transactional(readOnly = true)
    public List<CanchaResumenRespuesta> listarActivas() {
        List<Cancha> activas = canchas.findByEstadoOrderByOrdenAscIdAsc(EstadoCancha.ACTIVA);
        if (activas.isEmpty()) {
            return List.of();
        }
        Map<Long, String> principales = fotos.findByIdCanchaInAndPrincipalTrue(activas.stream().map(Cancha::getId).toList())
                .stream().collect(Collectors.toMap(CanchaFoto::getIdCancha, CanchaFoto::getArchivo, (a, b) -> a));
        Map<Long, CalificacionRespuesta> calificaciones = resenas.calificacionesPorCancha();
        CalificacionRespuesta sinResenas = new CalificacionRespuesta(Calificaciones.promedio(0, 0), 0);
        return activas.stream()
                .map(c -> mapeador.resumen(c, principales.get(c.getId()), calificaciones.getOrDefault(c.getId(), sinResenas)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CanchaDetalleRespuesta detallePublico(String idOSlug) {
        String clave = idOSlug == null ? "" : idOSlug.trim();
        Cancha c = (clave.matches("\\d{1,18}") ? canchas.findById(Long.valueOf(clave)) : canchas.findBySlug(clave.toLowerCase()))
                .filter(Cancha::estaActiva)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADA));
        return detalleDe(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanchaDetalleRespuesta> listarTodas() {
        return canchas.findAllByOrderByOrdenAscIdAsc().stream().map(this::detalleDe).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CanchaDetalleRespuesta detalle(Long id) {
        return detalleDe(buscar(id));
    }

    @Override
    @Transactional
    public CanchaDetalleRespuesta crear(CanchaSolicitud s) {
        String nombre = Texto.limpiar(s.nombre());
        validarNombreLibre(nombre, null);
        Cancha c = new Cancha();
        copiar(s, c, nombre);
        c.setSlug(slugLibre(nombre, null));
        c.setEstado(s.estado() == null ? EstadoCancha.ACTIVA : s.estado());
        c.setOrden(s.orden() == null ? canchas.maximoOrden() + 1 : s.orden());
        horarioPorDefecto(c);
        canchas.save(c);
        anunciar(c);
        auditor.registrar("CREAR_CANCHA", "cancha", c.getId(), resumenAuditoria(c));
        return detalleDe(c);
    }

    @Override
    @Transactional
    public CanchaDetalleRespuesta actualizar(Long id, CanchaSolicitud s) {
        Cancha c = buscar(id);
        String nombre = Texto.limpiar(s.nombre());
        validarNombreLibre(nombre, id);
        if (!nombre.equals(c.getNombre())) {
            c.setSlug(slugLibre(nombre, id));
        }
        copiar(s, c, nombre);
        if (s.estado() != null) {
            c.setEstado(s.estado());
        }
        if (s.orden() != null) {
            c.setOrden(s.orden());
        }
        canchas.saveAndFlush(c);
        anunciar(c);
        auditor.registrar("EDITAR_CANCHA", "cancha", c.getId(), resumenAuditoria(c));
        return detalleDe(c);
    }

    @Override
    @Transactional
    public CanchaDetalleRespuesta cambiarEstado(Long id, EstadoCancha estado) {
        Cancha c = buscar(id);
        if (c.getEstado() != estado) {
            EstadoCancha anterior = c.getEstado();
            c.setEstado(estado);
            canchas.saveAndFlush(c);
            anunciar(c);
            auditor.registrar("CAMBIAR_ESTADO_CANCHA", "cancha", c.getId(), c.getNombre() + " · " + anterior + " → " + estado);
        }
        return detalleDe(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanchaInternaRespuesta> listarInternas() {
        return canchas.findAllByOrderByOrdenAscIdAsc().stream().map(mapeador::interna).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CanchaInternaRespuesta interna(Long id) {
        return mapeador.interna(buscar(id));
    }

    private Cancha buscar(Long id) {
        return canchas.findById(id).orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADA));
    }

    private CanchaDetalleRespuesta detalleDe(Cancha c) {
        return mapeador.detalle(c, fotos.findByIdCanchaOrderByOrdenAscIdAsc(c.getId()), resenas.resumen(c.getId()));
    }

    private void validarNombreLibre(String nombre, Long idActual) {
        canchas.findByNombreIgnoreCase(nombre)
                .filter(otra -> !otra.getId().equals(idActual))
                .ifPresent(otra -> {
                    throw NegocioExcepcion.conflicto("CANCHA_DUPLICADA", "Ya existe una cancha llamada «" + otra.getNombre()
                            + "». Usa otro nombre.");
                });
    }

    /** cancha-1-futbol-5, cancha-1-futbol-5-2, … sin chocar con otra cancha. */
    private String slugLibre(String nombre, Long idActual) {
        String base = Slugs.de(nombre);
        String candidato = base;
        for (int n = 2; ; n++) {
            var existente = canchas.findBySlug(candidato);
            if (existente.isEmpty() || existente.get().getId().equals(idActual)) {
                return candidato;
            }
            candidato = base + "-" + n;
        }
    }

    private static void copiar(CanchaSolicitud s, Cancha c, String nombre) {
        c.setNombre(nombre);
        c.setDeporte(s.deporte());
        c.setSuperficie(Texto.limpiar(s.superficie()));
        c.setCapacidad(s.capacidad());
        c.setDescripcion(Texto.limpiar(s.descripcion()));
        c.setDescripcionLarga(s.descripcionLarga() == null || s.descripcionLarga().isBlank() ? null : s.descripcionLarga().trim());
        c.setTechada(s.techada());
        c.setIluminacionLed(s.iluminacionLed());
        c.setMedidas(s.medidas() == null || s.medidas().isBlank() ? null : Texto.limpiar(s.medidas()));
        c.setCaracteristicas(s.caracteristicas() == null ? new ArrayList<>() : s.caracteristicas().stream()
                .map(Texto::limpiar).distinct().collect(Collectors.toCollection(ArrayList::new)));
        c.setPrecioDia(Dinero.redondear(s.precioDia()));
        c.setPrecioNoche(Dinero.redondear(s.precioNoche()));
    }

    /** El evento (con el horario completo) se entrega a RabbitMQ después del commit (PublicadorCanchaActualizada). */
    private void anunciar(Cancha c) {
        eventos.publishEvent(mapeador.evento(c));
    }

    /**
     * Una cancha nueva abre todos los días con el horario global del complejo (por defecto 8:00 a. m.–11:00 p. m., turnos de
     * 60 min, noche desde las 6:00 p. m.) y en feriado abre con S/ 10 de adicional; luego se ajusta en «Horario y precios».
     */
    private void horarioPorDefecto(Cancha c) {
        Map<String, String> conf = configuracion.todas();
        int duracion = Integer.parseInt(conf.get(ClaveConfiguracion.DURACION_MIN.clave()));
        c.setDuracionTurnoMin(duracion);
        c.setInicioNoche(LocalTime.parse(conf.get(ClaveConfiguracion.INICIO_NOCHE.clave())));
        c.setHorario(new ArrayList<>(HorarioServicioImpl.semanaPorDefecto(
                LocalTime.parse(conf.get(ClaveConfiguracion.PRIMER_TURNO.clave())),
                LocalTime.parse(conf.get(ClaveConfiguracion.ULTIMO_TURNO.clave())), duracion)));
    }

    private static String resumenAuditoria(Cancha c) {
        return c.getNombre() + " · " + c.getDeporte().getTexto() + " · día " + Dinero.formato(c.getPrecioDia())
                + " / noche " + Dinero.formato(c.getPrecioNoche()) + " · " + c.getEstado();
    }
}
