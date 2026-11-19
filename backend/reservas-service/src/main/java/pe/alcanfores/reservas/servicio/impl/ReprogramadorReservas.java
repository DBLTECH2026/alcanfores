package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.ActorReprogramacion;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.modelo.ReservaReprogramacion;
import pe.alcanfores.reservas.modelo.ReservaReprogramacion.Horario;
import pe.alcanfores.reservas.modelo.ReservaTurno;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ReprogramacionRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaTurnoRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cambia el horario (fecha, hora y/o cancha) de una reserva vigente conservando lo pagado. Lo usan el portal del
 * cliente, Canchita y el panel; cada uno audita con su actor. Todo ocurre en UNA transacción:
 * <ol>
 *   <li>la reserva ya llega bloqueada ({@code SELECT … FOR UPDATE}): dos cambios a la vez se atienden en orden;</li>
 *   <li>se bloquean en orden de id los turnos viejos y los nuevos (el mismo orden que usa crear: sin interbloqueos);</li>
 *   <li>los turnos nuevos pasan las MISMAS reglas que al crear (cantidad, misma cancha y fecha, visibles, fecha
 *       elegible, a tiempo, cancha activa, sin bloqueo y libres); los que la reserva ya ocupa cuentan como libres;</li>
 *   <li>el nuevo total no puede ser menor que lo pagado más el comprobante en revisión y, para el cliente y Canchita,
 *       lo que ya pagó (si pagó algo) debe cubrir el adelanto del nuevo horario;</li>
 *   <li>se liberan los turnos que deja, se toman los nuevos, se recalcula el total con los precios vigentes (con
 *       recargo de feriado), el adelanto y el estado del pago; el índice UNIQUE de {@code reserva_turno.turno_activo}
 *       impide una doble reserva concurrente → 409 TURNO_OCUPADO y no cambia nada;</li>
 *   <li>se guarda el historial, el hito de la línea de tiempo y {@code reserva.reprogramada} para después del commit.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ReprogramadorReservas {

    public static final String CAMPO = "idsTurno";
    public static final String NO_REPROGRAMABLE = "RESERVA_NO_REPROGRAMABLE";
    public static final String NO_PERMITIDA = "REPROGRAMACION_NO_PERMITIDA";
    public static final String MONTO_MENOR = "REPROGRAMAR_MONTO_MENOR";
    public static final String ADELANTO_INSUFICIENTE = "REPROGRAMAR_ADELANTO_INSUFICIENTE";
    public static final String MISMO_HORARIO = "REPROGRAMAR_MISMO_HORARIO";
    public static final String MENSAJE_LIMITE = "Ya cambiaste el horario una vez; escríbenos para ayudarte.";
    public static final String MENSAJE_PLAZO = "Los cambios de horario se hacen hasta " + ReglasReserva.HORAS_REPROGRAMAR_CLIENTE
            + " horas antes del partido; escríbenos para ayudarte.";
    public static final String MENSAJE_MONTO_MENOR =
            "El nuevo horario cuesta menos de lo que ya pagaste. Elige otro horario o acércate al complejo.";
    public static final String MENSAJE_MISMO_HORARIO = "Elegiste el mismo horario que ya tienes. Elige otro día, hora o cancha.";
    public static final String MENSAJE_ADELANTO =
            "El nuevo horario pide un adelanto mayor del que ya pagaste. Elige otro horario o escríbenos para ayudarte.";
    public static final String MENSAJE_EMPEZO = "El partido de esta reserva ya empezó; ya no se puede cambiar el horario.";
    private static final Set<ActorReprogramacion> DEL_CLIENTE = Set.of(ActorReprogramacion.CLIENTE, ActorReprogramacion.CANCHITA);

    private final OperacionesReserva operaciones;
    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final ReservaTurnoRepositorio reservaTurnos;
    private final ReprogramacionRepositorio reprogramaciones;
    private final PlanificadorTurnos planificador;
    private final ComplejoServicio complejo;
    private final ReservaMapeador mapeador;

    /** Turnos nuevos (1 a 8), quién pide el cambio y, si es el panel, el usuario y el motivo. */
    public record Cambio(List<Long> idsTurno, ActorReprogramacion actor, String usuario, String motivo) {
    }

    /**
     * @param r reserva ya bloqueada con {@code ReservaRepositorio.bloquearPor…}
     * @return el registro del historial (horario anterior y nuevo)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservaReprogramacion reprogramar(Reserva r, Cambio c) {
        LocalDateTime ahora = Tiempo.ahora();
        validarReserva(r, c.actor(), ahora);
        OperacionesReserva.validarLista(c.idsTurno(), CAMPO);

        Set<Long> idsActuales = r.turnosOrdenados().stream().map(Turno::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> idsNuevos = new LinkedHashSet<>(c.idsTurno());
        if (idsActuales.equals(idsNuevos)) {
            throw NegocioExcepcion.regla(MISMO_HORARIO, MENSAJE_MISMO_HORARIO);
        }
        Set<Long> todos = new TreeSet<>(idsActuales);
        todos.addAll(idsNuevos);
        Map<Long, Turno> bloqueados = turnos.bloquearPorIds(todos).stream()
                .collect(Collectors.toMap(Turno::getId, Function.identity()));
        List<Turno> nuevos = OperacionesReserva.validarGrupo(
                idsNuevos.stream().map(bloqueados::get).filter(Objects::nonNull).toList(), idsNuevos.size(), CAMPO);
        operaciones.validarFecha(nuevos.get(0).getFecha(), ahora.toLocalDate(), CAMPO);
        boolean web = c.actor().delCliente();
        for (Turno t : nuevos) {
            if (idsActuales.contains(t.getId())) {
                operaciones.validarATiempoYActivo(t, web, ahora, CAMPO);
            } else {
                operaciones.validarLibre(t, web, ahora, CAMPO);
            }
        }
        BigDecimal total = Dinero.redondear(nuevos.stream().map(t -> Dinero.redondear(t.getPrecio()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal comprometido = ReglasReserva.montoComprometido(r);
        if (total.compareTo(comprometido) < 0) {
            throw NegocioExcepcion.regla(MONTO_MENOR, MENSAJE_MONTO_MENOR);
        }
        BigDecimal adelanto = Dinero.porcentaje(total, complejo.parametros().adelantoPct());
        if (web && !ReglasReserva.adelantoCubierto(comprometido, adelanto)) {
            throw NegocioExcepcion.regla(ADELANTO_INSUFICIENTE, MENSAJE_ADELANTO);
        }

        Horario antes = foto(r);
        cambiarTurnos(r, nuevos, idsActuales, bloqueados);
        r.setMontoTotal(total);
        r.setMontoAdelanto(adelanto);
        r.setEstadoPago(ReglasReserva.estadoPagoTrasCambio(r.getEstadoPago(), r.getMontoPagado(), total));
        LocalDateTime nuevoInicio = nuevos.get(0).inicio();
        if (r.getVenceEn() != null && r.getVenceEn().isAfter(nuevoInicio)) {
            r.setVenceEn(nuevoInicio);
        }
        try {
            reservas.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw OperacionesReserva.ocupado();
        }

        Horario despues = foto(r);
        String motivo = c.motivo() == null || c.motivo().isBlank() ? null : Texto.limpiar(c.motivo());
        ReservaReprogramacion rp = reprogramaciones.save(new ReservaReprogramacion(r.getId(), antes, despues, c.actor(),
                c.usuario(), motivo, ahora));
        operaciones.registrarHito(r, ReservaHistorial.REPROGRAMADA, textoHito(rp));
        operaciones.publicar(Eventos.RESERVA_REPROGRAMADA, new ReservaReprogramadaEvento(UUID.randomUUID(), ahora,
                mapeador.datos(r), r.getEstado().name(), r.saldo(), antes.cancha(), antes.fecha(), antes.horaInicio(),
                antes.horaFin(), antes.horarioTexto(), antes.total(), c.actor().name(), motivo, r.getEstadoPago().name(),
                r.getVenceEn()));
        return rp;
    }

    /** Cuántas veces cambió el horario el cliente (portal o Canchita): el límite es uno por reserva. */
    public long cambiosDelCliente(Reserva r) {
        return reprogramaciones.countByIdReservaAndActorIn(r.getId(), DEL_CLIENTE);
    }

    /** «Cancha 1 · 20/11/2026 7:00–8:00 p. m. (S/ 80.00) → Cancha 2 · 21/11/2026 8:00–9:00 p. m. (S/ 90.00)». */
    public static String resumen(ReservaReprogramacion rp) {
        return texto(rp.anterior()) + " → " + texto(rp.nuevo());
    }

    /**
     * Para todos: no cancelada, sin asistencia registrada y que el partido aún no empiece (aunque nadie haya marcado la
     * asistencia: un partido ya jugado no se pasa a otro día). Para el cliente y Canchita: plazo y un solo cambio.
     */
    private void validarReserva(Reserva r, ActorReprogramacion actor, LocalDateTime ahora) {
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw NegocioExcepcion.regla(NO_REPROGRAMABLE, "Esta reserva está cancelada; ya no se puede cambiar el horario.");
        }
        if (r.getAsistencia() != Asistencia.PENDIENTE) {
            throw NegocioExcepcion.regla(NO_REPROGRAMABLE,
                    "Ya se registró la asistencia de esta reserva; ya no se puede cambiar el horario.");
        }
        if (!ahora.isBefore(r.inicio())) {
            throw NegocioExcepcion.regla(NO_REPROGRAMABLE, MENSAJE_EMPEZO);
        }
        if (!actor.delCliente()) {
            return;
        }
        if (!ReglasReserva.aTiempoParaReprogramar(r, ahora)) {
            throw NegocioExcepcion.regla(NO_PERMITIDA, MENSAJE_PLAZO);
        }
        if (cambiosDelCliente(r) >= ReglasReserva.MAX_REPROGRAMACIONES_CLIENTE) {
            throw NegocioExcepcion.regla(NO_PERMITIDA, MENSAJE_LIMITE);
        }
    }

    /**
     * Quita los turnos que deja (filas de {@code reserva_turno} borradas y turnos liberados con el horario y calendario
     * vigentes), actualiza el precio de los que conserva y agrega los nuevos como RESERVADOS.
     */
    private void cambiarTurnos(Reserva r, List<Turno> nuevos, Set<Long> idsActuales, Map<Long, Turno> bloqueados) {
        Set<Long> idsNuevos = nuevos.stream().map(Turno::getId).collect(Collectors.toSet());
        for (Iterator<ReservaTurno> it = r.getTurnos().iterator(); it.hasNext(); ) {
            ReservaTurno rt = it.next();
            if (!idsNuevos.contains(rt.getTurno().getId())) {
                it.remove();
                reservaTurnos.delete(rt);
            }
        }
        idsActuales.stream().filter(id -> !idsNuevos.contains(id)).map(bloqueados::get).forEach(planificador::liberado);
        Map<Long, ReservaTurno> conservados = r.getTurnos().stream()
                .collect(Collectors.toMap(rt -> rt.getTurno().getId(), Function.identity()));
        for (Turno t : nuevos) {
            BigDecimal precio = Dinero.redondear(t.getPrecio());
            ReservaTurno propio = conservados.get(t.getId());
            if (propio != null) {
                propio.setPrecio(precio);
            } else {
                r.agregarTurno(t, precio);
            }
            t.setEstado(EstadoTurno.RESERVADO);
        }
        r.setTurno(nuevos.get(0));
    }

    private static Horario foto(Reserva r) {
        List<Turno> ts = r.turnosOrdenados();
        Turno primero = ts.get(0);
        return new Horario(primero.getIdCancha(), primero.getNombreCancha(), primero.getFecha(), r.horaInicio(), r.horaFin(),
                r.horarioTexto(), ts.stream().map(Turno::getId).toList(), r.getMontoTotal());
    }

    /**
     * «Horario cambiado por el cliente: Cancha 1 · 20/11/2026 7:00–8:00 p. m. (S/ 80.00) → …». La línea de tiempo la ve
     * el cliente: el motivo del panel es una nota interna y queda solo en el historial, la bitácora y el detalle admin.
     */
    private static String textoHito(ReservaReprogramacion rp) {
        String quien = switch (rp.getActor()) {
            case CLIENTE -> "por el cliente";
            case CANCHITA -> "por Canchita (asistente)";
            case PANEL -> "por el complejo";
        };
        return "Horario cambiado " + quien + ": " + resumen(rp) + ".";
    }

    private static String texto(Horario h) {
        return h.cancha() + " · " + h.fecha().format(Tiempo.FECHA_CORTA) + " " + h.horarioTexto() + " ("
                + Dinero.formato(h.total()) + ")";
    }
}
