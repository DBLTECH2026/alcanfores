package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.comun.eventos.CanchaActualizadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.util.Horarios;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.DiaEspecial;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaCancha;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaReserva;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.AgendaTurno;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.BloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.DesbloqueoRangoSolicitud;
import pe.alcanfores.reservas.dto.TurnoDtos.GeneracionRespuesta;
import pe.alcanfores.reservas.dto.TurnoDtos.GenerarTurnosSolicitud;
import pe.alcanfores.reservas.evento.IdempotenciaEventos;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaTurno;
import pe.alcanfores.reservas.modelo.TipoBloqueo;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ReservaTurnoRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TurnoServicioImpl implements TurnoServicio {

    private static final Logger log = LoggerFactory.getLogger(TurnoServicioImpl.class);
    private static final int MAX_DIAS_GENERACION = 60;
    private static final int MAX_DIAS_RANGO = 31;

    private final TurnoRepositorio turnos;
    private final ReservaTurnoRepositorio reservaTurnos;
    private final ComplejoServicio complejo;
    private final PlanificadorTurnos planificador;
    private final IdempotenciaEventos idempotencia;
    private final Auditor auditor;
    private final TransactionTemplate transacciones;

    @Override
    @Transactional
    public GeneracionRespuesta generarVentana() {
        LocalDate hoy = Tiempo.hoy();
        return generarRango(hoy, hoy.plusDays(complejo.parametros().horizonte()), List.of());
    }

    @Override
    @Transactional
    public GeneracionRespuesta generarRango(LocalDate desde, LocalDate hasta, Collection<Long> idsCancha) {
        int creados = 0;
        int existentes = 0;
        for (CanchaCatalogo c : complejo.canchas()) {
            if (idsCancha == null || idsCancha.isEmpty() || idsCancha.contains(c.id())) {
                int[] r = regenerar(c, desde, hasta);
                creados += r[0];
                existentes += r[1];
            }
        }
        if (creados > 0) {
            log.info("Turnos del {} al {}: {} nuevos, {} ya existían", desde, hasta, creados, existentes);
        }
        return new GeneracionRespuesta(creados, existentes);
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
        validarCanchas(ids);
        GeneracionRespuesta r = generarRango(s.desde(), s.hasta(), ids);
        auditor.registrar("GENERAR_TURNOS", "turno", null,
                "Del " + s.desde() + " al " + s.hasta() + ": " + r.creados() + " creados, " + r.existentes() + " ya existían");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public AgendaRespuesta agenda(LocalDate fecha, Long idCancha) {
        List<Turno> delDia = turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha).stream()
                .filter(Turno::visible)
                .filter(t -> idCancha == null || idCancha.equals(t.getIdCancha())).toList();
        Map<Long, Reserva> porTurno = vigentesPorTurno(delDia);
        Map<Long, List<Turno>> porCancha = delDia.stream().collect(Collectors.groupingBy(Turno::getIdCancha));
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
        Turno t = turnos.bloquearPorId(idTurno).filter(Turno::visible).orElseThrow(TurnoServicioImpl::turnoNoEncontrado);
        if (!Tiempo.ahora().isBefore(t.fin())) {
            throw NegocioExcepcion.regla("TURNO_PASADO", "No puedes bloquear un turno que ya pasó.");
        }
        if (t.getEstado() == EstadoTurno.RESERVADO || reservaTurnos.existsByTurnoIdAndVigenteTrue(t.getId())) {
            throw NegocioExcepcion.conflicto("TURNO_CON_RESERVA", "Ese turno tiene una reserva vigente; cancélala primero.");
        }
        t.bloquear(TipoBloqueo.MANUAL, Texto.limpiar(motivo));
        auditor.registrar("BLOQUEAR_TURNO", "turno", t.getId(), descripcion(t) + " · " + t.getMotivoBloqueo());
        return agendaTurno(t, null);
    }

    @Override
    @Transactional
    public AgendaTurno desbloquear(Long idTurno, String motivo) {
        Turno t = turnos.bloquearPorId(idTurno).filter(Turno::visible).orElseThrow(TurnoServicioImpl::turnoNoEncontrado);
        if (t.getEstado() != EstadoTurno.BLOQUEADO) {
            throw NegocioExcepcion.regla("TURNO_NO_BLOQUEADO", "Ese turno no está bloqueado.");
        }
        CanchaCatalogo c = complejo.cancha(t.getIdCancha()).orElse(null);
        if (t.getTipoBloqueo() == TipoBloqueo.CANCHA_INACTIVA || (c != null && !c.activa())) {
            throw NegocioExcepcion.regla("CANCHA_INACTIVA", "La cancha está inactiva; actívala en Canchas para liberar sus turnos.");
        }
        if (t.getTipoBloqueo() == TipoBloqueo.FERIADO || t.getTipoBloqueo() == TipoBloqueo.NO_LABORABLE) {
            throw NegocioExcepcion.regla("BLOQUEO_DE_CALENDARIO", "Ese día está cerrado en el calendario; ábrelo desde "
                    + "Configuración · Feriados y días no laborables.");
        }
        planificador.liberado(t);
        String detalle = motivo == null || motivo.isBlank() ? "" : " · " + Texto.limpiar(motivo);
        auditor.registrar("DESBLOQUEAR_TURNO", "turno", t.getId(), descripcion(t) + detalle);
        return agendaTurno(t, null);
    }

    @Override
    @Transactional
    public BloqueoRangoRespuesta bloquearRango(BloqueoRangoSolicitud s) {
        List<Long> ids = canchasDe(s.idsCancha(), s.idCancha());
        LocalDate hasta = validarRango(ids, s.fecha(), s.fechaHasta(), s.horaDesde(), s.horaHasta());
        List<Turno> rango = enRango(ids, s.fecha(), hasta, s.horaDesde(), s.horaHasta());
        Map<Long, Reserva> vigentes = vigentesPorTurno(rango);
        String motivo = Texto.limpiar(s.motivo());
        int bloqueados = 0;
        Set<String> omitidas = new TreeSet<>();
        for (Turno t : rango) {
            Reserva r = vigentes.get(t.getId());
            if (r != null) {
                omitidas.add(r.getCodigoReserva());
            } else if (t.getEstado() == EstadoTurno.DISPONIBLE) {
                t.bloquear(TipoBloqueo.MANUAL, motivo);
                bloqueados++;
            }
        }
        auditor.registrar("BLOQUEAR_RANGO", "turno", null, nombres(ids) + " · " + periodo(s.fecha(), hasta, s.horaDesde(),
                s.horaHasta()) + " · " + bloqueados + " bloqueados · " + motivo);
        return new BloqueoRangoRespuesta(bloqueados, List.copyOf(omitidas));
    }

    @Override
    @Transactional
    public DesbloqueoRangoRespuesta desbloquearRango(DesbloqueoRangoSolicitud s) {
        List<Long> ids = canchasDe(s.idsCancha(), s.idCancha());
        LocalDate hasta = validarRango(ids, s.fecha(), s.fechaHasta(), s.horaDesde(), s.horaHasta());
        int desbloqueados = 0;
        for (Turno t : enRango(ids, s.fecha(), hasta, s.horaDesde(), s.horaHasta())) {
            if (t.getEstado() == EstadoTurno.BLOQUEADO && t.getTipoBloqueo() == TipoBloqueo.MANUAL) {
                planificador.liberado(t);
                desbloqueados++;
            }
        }
        auditor.registrar("DESBLOQUEAR_RANGO", "turno", null, nombres(ids) + " · " + periodo(s.fecha(), hasta,
                s.horaDesde(), s.horaHasta()) + " · " + desbloqueados + " desbloqueados");
        return new DesbloqueoRangoRespuesta(desbloqueados);
    }

    @Override
    @Transactional
    public void aplicarCambioCancha(CanchaCatalogo cancha) {
        regenerarFuturos(cancha);
    }

    @Override
    public void sincronizarCatalogo() {
        List<CanchaCatalogo> cambiadas = complejo.refrescar();
        List<LocalDate> fechas = complejo.refrescarCalendario();
        for (CanchaCatalogo c : cambiadas) {
            transacciones.executeWithoutResult(estado -> regenerarFuturos(c));
        }
        Set<Long> yaHechas = cambiadas.stream().map(CanchaCatalogo::id).collect(Collectors.toSet());
        for (LocalDate f : fechas) {
            if (dentroDelHorizonte(f)) {
                transacciones.executeWithoutResult(estado -> complejo.canchas().stream()
                        .filter(c -> !yaHechas.contains(c.id())).forEach(c -> regenerar(c, f, f)));
            }
        }
    }

    @Override
    public void sincronizarSiFalta() {
        if (!complejo.catalogoLeido()) {
            sincronizarCatalogo();
        }
    }

    @Override
    @Transactional
    public void alActualizarCancha(CanchaActualizadaEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.CANCHA_ACTUALIZADA)) {
            return;
        }
        sincronizarSiFalta(); // si catálogo publica un evento, ya responde: sin esto el evento solo crearía turnos
        CanchaCatalogo c = new CanchaCatalogo(e.idCancha(), e.nombre(), e.deporte(), e.precioDia(), e.precioNoche(), e.estado(),
                e.duracionTurnoMin(), e.inicioNoche(), e.dias(), e.feriadoModo(), e.recargoFeriado());
        complejo.actualizarCancha(c);
        regenerarFuturos(c);
    }

    @Override
    @Transactional
    public void alActualizarCalendario(CalendarioActualizadoEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.CALENDARIO_ACTUALIZADO)) {
            return;
        }
        sincronizarSiFalta();
        complejo.actualizarDia(e.fecha(), e.eliminado() ? null : DiaEspecial.de(e));
        if (dentroDelHorizonte(e.fecha())) {
            complejo.canchas().forEach(c -> regenerar(c, e.fecha(), e.fecha()));
        }
        log.info("Calendario {} {} ({}) aplicado a los turnos", e.fecha(), e.eliminado() ? "quitado" : e.tipo(), e.nombre());
    }

    private void regenerarFuturos(CanchaCatalogo c) {
        LocalDate hoy = Tiempo.hoy();
        int[] r = regenerar(c, hoy, hoy.plusDays(complejo.parametros().horizonte()));
        log.info("Cancha {} ({}) regenerada: {} turnos nuevos, {} actualizados", c.id(), c.activa() ? "activa" : "inactiva",
                r[0], r[1]);
    }

    /**
     * Deja los turnos de la cancha entre dos fechas como manda su horario y el calendario. Se conservan (solo se les
     * actualiza el nombre) los que tienen reserva, los bloqueos manuales y los que ya empezaron; un turno nuevo que se
     * cruce con uno conservado no se crea. Los libres que ya no calzan se borran o, si una reserva cancelada los
     * referencia, quedan ocultos (FUERA_DE_HORARIO). Si catálogo aún no respondió (copia semilla), solo se crean los que
     * faltan: no se cambia ni se borra nada con datos que podrían no ser los reales.
     *
     * @return {creados, existentes}
     */
    private int[] regenerar(CanchaCatalogo c, LocalDate desde, LocalDate hasta) {
        LocalDateTime ahora = Tiempo.ahora();
        boolean soloCrear = !complejo.catalogoLeido();
        List<Turno> existentes = turnos.bloquearDeCanchaEntre(c.id(), desde, hasta);
        List<Long> idsExistentes = existentes.stream().map(Turno::getId).toList();
        Set<Long> conReserva = idsExistentes.isEmpty() ? Set.of() : reservaTurnos.vigentesDeTurnos(idsExistentes).stream()
                .map(rt -> rt.getTurno().getId()).collect(Collectors.toSet());
        Set<Long> referenciados = idsExistentes.isEmpty() ? Set.of() : new HashSet<>(reservaTurnos.referenciados(idsExistentes));
        Map<LocalDate, List<Turno>> porFecha = existentes.stream().collect(Collectors.groupingBy(Turno::getFecha));
        List<Turno> nuevos = new ArrayList<>();
        List<Turno> borrar = new ArrayList<>();
        int actualizados = 0;
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            List<Turno> delDia = porFecha.getOrDefault(f, List.of());
            List<Turno> fijos = delDia.stream().filter(t -> soloCrear || t.getEstado() == EstadoTurno.RESERVADO
                    || conReserva.contains(t.getId()) || t.getTipoBloqueo() == TipoBloqueo.MANUAL
                    || !t.inicio().isAfter(ahora)).toList();
            Map<LocalTime, Turno> porInicio = new HashMap<>();
            delDia.forEach(t -> porInicio.put(t.getHoraInicio(), t));
            Set<Turno> atendidos = new LinkedHashSet<>(fijos);
            for (Horarios.Tramo tramo : planificador.tramos(c, f)) {
                Turno t = porInicio.get(tramo.inicio());
                if (t != null && fijos.contains(t)) {
                    actualizados++;
                } else if (!seCruza(tramo, fijos)) {
                    if (t == null) {
                        nuevos.add(nuevoTurno(c, f, tramo));
                    } else {
                        t.setHoraFin(tramo.fin());
                        planificador.aplicar(t, planificador.plan(c, f, tramo.inicio()));
                        atendidos.add(t);
                        actualizados++;
                    }
                }
            }
            for (Turno t : delDia) {
                if (!soloCrear) {
                    copiarDatos(t, c);
                }
                if (atendidos.contains(t)) {
                    continue;
                }
                if (referenciados.contains(t.getId())) {
                    t.bloquear(TipoBloqueo.FUERA_DE_HORARIO, PlanificadorTurnos.MOTIVO_FUERA_DE_HORARIO);
                } else {
                    borrar.add(t);
                }
            }
        }
        turnos.deleteAll(borrar);
        turnos.flush();
        turnos.saveAll(nuevos);
        return new int[]{nuevos.size(), actualizados};
    }

    private static boolean seCruza(Horarios.Tramo tramo, List<Turno> fijos) {
        int a = Horarios.minutos(tramo.inicio());
        int b = Horarios.minutos(tramo.fin());
        return fijos.stream().anyMatch(t -> a < Horarios.minutos(t.getHoraFin()) && Horarios.minutos(t.getHoraInicio()) < b);
    }

    private Turno nuevoTurno(CanchaCatalogo c, LocalDate fecha, Horarios.Tramo tramo) {
        Turno t = new Turno();
        t.setIdCancha(c.id());
        copiarDatos(t, c);
        t.setFecha(fecha);
        t.setHoraInicio(tramo.inicio());
        t.setHoraFin(tramo.fin());
        planificador.aplicar(t, planificador.plan(c, fecha, tramo.inicio()));
        return t;
    }

    private static void copiarDatos(Turno t, CanchaCatalogo c) {
        t.setNombreCancha(recortar(c.nombre()));
        t.setDeporte(c.deporte());
    }

    private boolean dentroDelHorizonte(LocalDate fecha) {
        LocalDate hoy = Tiempo.hoy();
        return fecha != null && !fecha.isBefore(hoy) && !fecha.isAfter(hoy.plusDays(complejo.parametros().horizonte()));
    }

    /** {@code idsCancha} o, si no vino, {@code idCancha} suelto (formato v1). */
    private List<Long> canchasDe(List<Long> idsCancha, Long idCancha) {
        List<Long> ids = idsCancha != null && !idsCancha.isEmpty() ? idsCancha.stream().distinct().toList()
                : idCancha == null ? List.of() : List.of(idCancha);
        if (ids.isEmpty()) {
            throw NegocioExcepcion.validacion("idsCancha", "Elige al menos una cancha.");
        }
        validarCanchas(ids);
        return ids;
    }

    private void validarCanchas(List<Long> ids) {
        for (Long id : ids) {
            if (complejo.cancha(id).isEmpty()) {
                throw NegocioExcepcion.validacion("idsCancha", "La cancha " + id + " no existe.");
            }
        }
    }

    /** @return la fecha final del rango (la misma fecha si no vino {@code fechaHasta}) */
    private static LocalDate validarRango(List<Long> ids, LocalDate fecha, LocalDate fechaHasta, LocalTime desde,
                                          LocalTime hasta) {
        LocalDate fin = fechaHasta == null ? fecha : fechaHasta;
        if (fecha.isBefore(Tiempo.hoy())) {
            throw NegocioExcepcion.validacion("fecha", "No puedes cambiar fechas pasadas.");
        }
        if (fin.isBefore(fecha)) {
            throw NegocioExcepcion.validacion("fechaHasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(fecha, fin) >= MAX_DIAS_RANGO) {
            throw NegocioExcepcion.validacion("fechaHasta", "Elige como máximo " + MAX_DIAS_RANGO + " días.");
        }
        if (Horarios.minutos(hasta) <= Horarios.minutos(desde)) {
            throw NegocioExcepcion.validacion("horaHasta", "La hora final debe ser posterior a la inicial.");
        }
        return fin;
    }

    /** Turnos visibles que aún no terminan y se cruzan con las horas desde–hasta (bloqueados para actualizarlos). */
    private List<Turno> enRango(List<Long> ids, LocalDate desde, LocalDate hasta, LocalTime horaDesde, LocalTime horaHasta) {
        LocalDateTime ahora = Tiempo.ahora();
        int a = Horarios.minutos(horaDesde);
        int b = Horarios.minutos(horaHasta);
        List<Turno> rango = turnos.bloquearDeCanchasEntre(ids, desde, hasta).stream()
                .filter(Turno::visible)
                .filter(t -> ahora.isBefore(t.fin()))
                .filter(t -> Horarios.minutos(t.getHoraInicio()) < b && a < Horarios.minutos(t.getHoraFin()))
                .toList();
        if (rango.isEmpty()) {
            throw NegocioExcepcion.regla("SIN_TURNOS", "No hay turnos en ese horario.");
        }
        return rango;
    }

    private String nombres(List<Long> ids) {
        return ids.stream().map(id -> complejo.cancha(id).map(CanchaCatalogo::nombre).orElse("Cancha " + id))
                .collect(Collectors.joining(", "));
    }

    private static String periodo(LocalDate desde, LocalDate hasta, LocalTime horaDesde, LocalTime horaHasta) {
        String dias = desde.equals(hasta) ? desde.toString() : desde + " al " + hasta;
        return dias + " de " + Tiempo.horaAmPm(horaDesde) + " a " + Tiempo.horaAmPm(horaHasta);
    }

    /** Reserva vigente de cada turno (una reserva de varios turnos aparece en todos). */
    private Map<Long, Reserva> vigentesPorTurno(List<Turno> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return reservaTurnos.vigentesDeTurnos(lista.stream().map(Turno::getId).toList()).stream()
                .collect(Collectors.toMap(rt -> rt.getTurno().getId(), ReservaTurno::getReserva, (x, y) -> x));
    }

    private static AgendaTurno agendaTurno(Turno t, Reserva r) {
        AgendaReserva ar = r == null ? null : new AgendaReserva(r.getCodigoReserva(), r.getCliente().nombreCompleto(),
                r.getCliente().getTelefono(), r.getEstado(), r.getEstadoPago(), r.getAsistencia());
        return new AgendaTurno(t.getId(), t.getHoraInicio(), t.getHoraFin(), t.getPrecio(), t.getFranja(), t.getEstado(),
                t.getMotivoBloqueo(), ar, t.isFeriado(), t.getRecargo(), t.getTipoBloqueo());
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
