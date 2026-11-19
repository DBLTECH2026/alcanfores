package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaCancha;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaReserva;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaTurno;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.GeneracionRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.GenerarTurnosSolicitud;
import pe.alcanfores.reservas.evento.IdempotenciaEventos;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TurnoServicioImpl implements TurnoServicio {

    private static final Logger log = LoggerFactory.getLogger(TurnoServicioImpl.class);
    private static final int MAX_DIAS_GENERACION = 60;
    private static final List<EstadoReserva> VIGENTES = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);

    private final TurnoRepositorio turnos;
    private final ReservaRepositorio reservas;
    private final ComplejoServicio complejo;
    private final IdempotenciaEventos idempotencia;
    private final Auditor auditor;
    private final TransactionTemplate transacciones;

    @Override
    @Transactional
    public GeneracionRespuesta generarVentana() {
        LocalDate hoy = Tiempo.hoy();
        return generarRango(hoy, hoy.plusDays(complejo.parametros().diasVisibles()), List.of());
    }

    @Override
    @Transactional
    public GeneracionRespuesta generarRango(LocalDate desde, LocalDate hasta, Collection<Long> idsCancha) {
        ParametrosComplejo p = complejo.parametros();
        List<CanchaCatalogo> canchas = complejo.canchas().stream()
                .filter(c -> idsCancha == null || idsCancha.isEmpty() || idsCancha.contains(c.id())).toList();
        Set<String> existentes = new HashSet<>();
        for (Object[] fila : turnos.clavesEnRango(desde, hasta)) {
            existentes.add(clave((Long) fila[0], (LocalDate) fila[1], (LocalTime) fila[2]));
        }
        List<LocalTime> horas = p.horasDeInicio();
        List<Turno> nuevos = new ArrayList<>();
        int yaExistian = 0;
        for (CanchaCatalogo c : canchas) {
            for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
                for (LocalTime h : horas) {
                    if (existentes.contains(clave(c.id(), f, h))) {
                        yaExistian++;
                    } else {
                        nuevos.add(nuevoTurno(c, f, h, p));
                    }
                }
            }
        }
        turnos.saveAll(nuevos);
        if (!nuevos.isEmpty()) {
            log.info("Turnos generados del {} al {}: {} nuevos, {} ya existían", desde, hasta, nuevos.size(), yaExistian);
        }
        return new GeneracionRespuesta(nuevos.size(), yaExistian);
    }

    @Override
    @Transactional
    public GeneracionRespuesta generar(GenerarTurnosSolicitud s) {
        LocalDate hoy = Tiempo.hoy();
        if (s.hasta().isBefore(s.desde())) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        if (s.desde().isBefore(hoy)) {
            throw NegocioExcepcion.validacion("desde", "La fecha inicial no puede ser anterior a hoy.");
        }
        if (ChronoUnit.DAYS.between(s.desde(), s.hasta()) >= MAX_DIAS_GENERACION) {
            throw NegocioExcepcion.validacion("hasta", "Genera como máximo " + MAX_DIAS_GENERACION + " días por vez.");
        }
        List<Long> ids = s.idsCancha() == null ? List.of() : s.idsCancha();
        for (Long id : ids) {
            if (complejo.cancha(id).isEmpty()) {
                throw NegocioExcepcion.validacion("idsCancha", "La cancha " + id + " no existe.");
            }
        }
        GeneracionRespuesta r = generarRango(s.desde(), s.hasta(), ids);
        auditor.registrar("GENERAR_TURNOS", "turno", null,
                "Del " + s.desde() + " al " + s.hasta() + ": " + r.creados() + " creados, " + r.existentes() + " ya existían");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public AgendaRespuesta agenda(LocalDate fecha, Long idCancha) {
        List<Turno> delDia = turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha).stream()
                .filter(t -> idCancha == null || idCancha.equals(t.getIdCancha())).toList();
        Map<Long, Reserva> porTurno = vigentesPorTurno(delDia);
        Map<Long, List<Turno>> porCancha = delDia.stream()
                .collect(Collectors.groupingBy(Turno::getIdCancha, LinkedHashMap::new, Collectors.toList()));
        List<AgendaCancha> canchas = new ArrayList<>();
        for (CanchaCatalogo c : complejo.canchas()) {
            if (idCancha == null || idCancha.equals(c.id())) {
                List<AgendaTurno> ts = porCancha.getOrDefault(c.id(), List.of()).stream()
                        .map(t -> agendaTurno(t, porTurno.get(t.getId()))).toList();
                canchas.add(new AgendaCancha(c.id(), c.nombre(), c.deporte(), c.activa(), ts));
            }
        }
        return new AgendaRespuesta(fecha, canchas);
    }

    @Override
    @Transactional
    public AgendaTurno bloquear(Long idTurno, String motivo) {
        Turno t = turnos.bloquearPorId(idTurno).orElseThrow(TurnoServicioImpl::turnoNoEncontrado);
        if (!Tiempo.ahora().isBefore(t.fin())) {
            throw NegocioExcepcion.regla("TURNO_PASADO", "No puedes bloquear un turno que ya pasó.");
        }
        if (t.getEstado() == EstadoTurno.RESERVADO || !reservas.deTurnoEnEstados(t.getId(), VIGENTES).isEmpty()) {
            throw NegocioExcepcion.conflicto("TURNO_CON_RESERVA", "Ese turno tiene una reserva vigente; cancélala primero.");
        }
        t.setEstado(EstadoTurno.BLOQUEADO);
        t.setMotivoBloqueo(Texto.limpiar(motivo));
        auditor.registrar("BLOQUEAR_TURNO", "turno", t.getId(), descripcion(t) + " · " + t.getMotivoBloqueo());
        return agendaTurno(t, null);
    }

    @Override
    @Transactional
    public AgendaTurno desbloquear(Long idTurno, String motivo) {
        Turno t = turnos.bloquearPorId(idTurno).orElseThrow(TurnoServicioImpl::turnoNoEncontrado);
        if (t.getEstado() != EstadoTurno.BLOQUEADO) {
            throw NegocioExcepcion.regla("TURNO_NO_BLOQUEADO", "Ese turno no está bloqueado.");
        }
        CanchaCatalogo c = complejo.cancha(t.getIdCancha()).orElse(null);
        if (c != null && !c.activa()) {
            throw NegocioExcepcion.regla("CANCHA_INACTIVA", "La cancha está inactiva; actívala en Canchas para liberar sus turnos.");
        }
        t.setEstado(EstadoTurno.DISPONIBLE);
        t.setMotivoBloqueo(null);
        if (c != null) {
            t.setPrecio(c.precio(t.getFranja()));
        }
        String detalle = motivo == null || motivo.isBlank() ? "" : " · " + Texto.limpiar(motivo);
        auditor.registrar("DESBLOQUEAR_TURNO", "turno", t.getId(), descripcion(t) + detalle);
        return agendaTurno(t, null);
    }

    @Override
    @Transactional
    public BloqueoRangoRespuesta bloquearRango(BloqueoRangoSolicitud s) {
        if (!s.horaHasta().isAfter(s.horaDesde())) {
            throw NegocioExcepcion.validacion("horaHasta", "La hora final debe ser posterior a la inicial.");
        }
        if (s.fecha().isBefore(Tiempo.hoy())) {
            throw NegocioExcepcion.validacion("fecha", "No puedes bloquear fechas pasadas.");
        }
        if (complejo.cancha(s.idCancha()).isEmpty()) {
            throw NegocioExcepcion.noEncontrado("Esa cancha no existe.");
        }
        List<Turno> rango = turnos.bloquearRango(s.idCancha(), s.fecha(), s.horaDesde(), s.horaHasta());
        if (rango.isEmpty()) {
            throw NegocioExcepcion.regla("SIN_TURNOS", "No hay turnos generados en ese horario.");
        }
        Map<Long, Reserva> vigentes = vigentesPorTurno(rango);
        LocalDateTime ahora = Tiempo.ahora();
        String motivo = Texto.limpiar(s.motivo());
        int bloqueados = 0;
        List<LocalTime> omitidas = new ArrayList<>();
        for (Turno t : rango) {
            if (!ahora.isBefore(t.fin())) {
                continue;
            }
            if (t.getEstado() == EstadoTurno.RESERVADO || vigentes.containsKey(t.getId())) {
                omitidas.add(t.getHoraInicio());
            } else {
                t.setEstado(EstadoTurno.BLOQUEADO);
                t.setMotivoBloqueo(motivo);
                bloqueados++;
            }
        }
        auditor.registrar("BLOQUEAR_RANGO", "turno", null, rango.get(0).getNombreCancha() + " · " + s.fecha() + " de "
                + s.horaDesde() + " a " + s.horaHasta() + " · " + bloqueados + " bloqueados · " + motivo);
        return new BloqueoRangoRespuesta(bloqueados, omitidas.size(), omitidas);
    }

    @Override
    @Transactional
    public void aplicarCambioCancha(CanchaCatalogo cancha) {
        aplicar(cancha);
    }

    @Override
    public void sincronizarCatalogo() {
        List<CanchaCatalogo> cambiadas = complejo.refrescar();
        for (CanchaCatalogo c : cambiadas) {
            transacciones.executeWithoutResult(estado -> aplicar(c));
        }
    }

    @Override
    @Transactional
    public void alActualizarCancha(CanchaActualizadaEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.CANCHA_ACTUALIZADA)) {
            return;
        }
        CanchaCatalogo c = new CanchaCatalogo(e.idCancha(), e.nombre(), e.deporte(), e.precioDia(), e.precioNoche(), e.estado());
        complejo.actualizarCancha(c);
        aplicar(c);
    }

    private void aplicar(CanchaCatalogo c) {
        LocalDate hoy = Tiempo.hoy();
        int cambiados = 0;
        for (Turno t : turnos.bloquearDeCanchaDesde(c.id(), hoy)) {
            t.setNombreCancha(recortar(c.nombre()));
            t.setDeporte(c.deporte());
            if (!c.activa() && t.getEstado() == EstadoTurno.DISPONIBLE) {
                t.setEstado(EstadoTurno.BLOQUEADO);
                t.setMotivoBloqueo(Turno.MOTIVO_CANCHA_INACTIVA);
            } else if (c.activa() && t.getEstado() == EstadoTurno.BLOQUEADO
                    && Turno.MOTIVO_CANCHA_INACTIVA.equals(t.getMotivoBloqueo())) {
                t.setEstado(EstadoTurno.DISPONIBLE);
                t.setMotivoBloqueo(null);
            }
            if (t.getEstado() == EstadoTurno.DISPONIBLE) {
                t.setPrecio(c.precio(t.getFranja()));
            }
            cambiados++;
        }
        generarRango(hoy, hoy.plusDays(complejo.parametros().diasVisibles()), List.of(c.id()));
        log.info("Cancha {} ({}) aplicada a {} turnos futuros", c.id(), c.activa() ? "activa" : "inactiva", cambiados);
    }

    private Turno nuevoTurno(CanchaCatalogo c, LocalDate fecha, LocalTime hora, ParametrosComplejo p) {
        Turno t = new Turno();
        t.setIdCancha(c.id());
        t.setNombreCancha(recortar(c.nombre()));
        t.setDeporte(c.deporte());
        t.setFecha(fecha);
        t.setHoraInicio(hora);
        t.setHoraFin(hora.plusMinutes(p.duracionMin()));
        t.setFranja(p.franja(hora));
        t.setPrecio(c.precio(t.getFranja()));
        if (c.activa()) {
            t.setEstado(EstadoTurno.DISPONIBLE);
        } else {
            t.setEstado(EstadoTurno.BLOQUEADO);
            t.setMotivoBloqueo(Turno.MOTIVO_CANCHA_INACTIVA);
        }
        return t;
    }

    private Map<Long, Reserva> vigentesPorTurno(List<Turno> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return reservas.deTurnosEnEstados(lista.stream().map(Turno::getId).toList(), VIGENTES).stream()
                .collect(Collectors.toMap(r -> r.getTurno().getId(), Function.identity(), (a, b) -> a));
    }

    private static AgendaTurno agendaTurno(Turno t, Reserva r) {
        AgendaReserva ar = r == null ? null : new AgendaReserva(r.getCodigoReserva(), r.getCliente().nombreCompleto(),
                r.getCliente().getTelefono(), r.getEstado(), r.getEstadoPago(), r.getAsistencia());
        return new AgendaTurno(t.getId(), t.getHoraInicio(), t.getHoraFin(), t.getPrecio(), t.getFranja(), t.getEstado(),
                t.getMotivoBloqueo(), ar);
    }

    private static String clave(Long idCancha, LocalDate fecha, LocalTime hora) {
        return idCancha + "|" + fecha + "|" + hora.withSecond(0).withNano(0);
    }

    private static String descripcion(Turno t) {
        return t.getNombreCancha() + " · " + t.getFecha() + " " + t.getHoraInicio();
    }

    private static String recortar(String nombre) {
        return nombre == null ? "" : nombre.length() > 60 ? nombre.substring(0, 60) : nombre;
    }

    private static NegocioExcepcion turnoNoEncontrado() {
        return NegocioExcepcion.noEncontrado("No encontramos ese turno.");
    }
}
