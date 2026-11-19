package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.ReservaDtos.ClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.TurnosPedidos;
import pe.alcanfores.reservas.evento.EventoParaPublicar;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.ConsultaDocumento;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.modelo.TipoDocumento;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.CancelacionRepositorio;
import pe.alcanfores.reservas.repositorio.ClienteRepositorio;
import pe.alcanfores.reservas.repositorio.ConsultaDocumentoRepositorio;
import pe.alcanfores.reservas.repositorio.HistorialRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaTurnoRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Operaciones de dominio que comparten el portal, el panel, los consumidores y las tareas: crear una reserva con
 * bloqueo del turno, cancelarla, escribir la línea de tiempo y encolar los eventos para después del commit.
 */
@Component
@RequiredArgsConstructor
public class OperacionesReserva {

    public static final String MENSAJE_OCUPADO = "Ese turno acaba de ser reservado por otra persona. Elige otro horario.";
    public static final String MENSAJE_NO_ENCONTRADA = "No encontramos una reserva con ese código y DNI. Revisa ambos datos.";
    private static final SecureRandom AZAR = new SecureRandom();

    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final ReservaTurnoRepositorio reservaTurnos;
    private final PlanificadorTurnos planificador;
    private final ClienteRepositorio clientes;
    private final CancelacionRepositorio cancelaciones;
    private final HistorialRepositorio historial;
    private final ConsultaDocumentoRepositorio consultas;
    private final ComplejoServicio complejo;
    private final ReservaMapeador mapeador;
    private final ApplicationEventPublisher eventos;

    /** Datos para crear una reserva (web o manual) de 1 a 8 turnos. */
    public record NuevaReserva(TurnosPedidos turnos, ClienteSolicitud cliente, Origen origen, String metodoPagoPrevisto,
                               String nota, Long registradoPor, String registradoPorUsuario) {
    }

    /**
     * Crea la reserva PENDIENTE/SIN_PAGO de 1 a 8 turnos de la misma cancha y fecha (pueden no ser seguidos): bloquea los
     * turnos con {@code SELECT … FOR UPDATE} en orden de id (dos reservas que comparten un turno no se interbloquean),
     * valida las reglas del §3, hace upsert del cliente por DNI y deja {@code reserva.registrada} para después del commit.
     * Total = suma de los precios (con recargo de feriado); adelanto = total × adelantoPct. Si aun así dos reservas chocan,
     * el índice UNIQUE de {@code reserva_turno.turno_activo} lo impide → 409 TURNO_OCUPADO.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reserva crear(NuevaReserva n) {
        ParametrosComplejo p = complejo.parametros();
        LocalDateTime ahora = Tiempo.ahora();
        LocalDate hoy = ahora.toLocalDate();
        boolean web = n.origen() == Origen.WEB;
        List<Turno> ts = bloquearTurnos(n.turnos());
        Turno primero = ts.get(0);
        if (primero.getFecha().isBefore(hoy) || primero.getFecha().isAfter(hoy.plusDays(p.diasVisibles()))) {
            throw NegocioExcepcion.validacion("fecha", Mensajes.rangoFecha(p.diasVisibles()));
        }
        for (Turno t : ts) {
            validarLibre(t, web, ahora, n.turnos().campo());
        }

        Reserva r = new Reserva();
        r.setCodigoReserva(nuevoCodigo());
        r.setCliente(guardarCliente(n.cliente(), ahora, !web));
        r.setEstado(EstadoReserva.PENDIENTE);
        r.setEstadoPago(EstadoPago.SIN_PAGO);
        BigDecimal total = BigDecimal.ZERO;
        for (Turno t : ts) {
            BigDecimal precio = Dinero.redondear(t.getPrecio());
            r.agregarTurno(t, precio);
            total = total.add(precio);
            t.setEstado(EstadoTurno.RESERVADO);
        }
        r.setOrigen(n.origen());
        r.setMetodoPagoPrevisto(n.metodoPagoPrevisto());
        r.setMontoTotal(Dinero.redondear(total));
        r.setMontoAdelanto(Dinero.porcentaje(total, p.adelantoPct()));
        r.setMontoPagado(BigDecimal.ZERO.setScale(2));
        r.setVenceEn(web ? min(ahora.plusMinutes(p.minutosParaPagar()), primero.inicio()) : null);
        r.setFechaRegistro(ahora);
        r.setRegistradoPor(n.registradoPor());
        r.setNota(n.nota() == null || n.nota().isBlank() ? null : Texto.limpiar(n.nota()));
        try {
            reservas.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw ocupado();
        }

        registrarHito(r, ReservaHistorial.REGISTRADA, textoRegistro(r, n));
        publicar(Eventos.RESERVA_REGISTRADA, new ReservaRegistradaEvento(UUID.randomUUID(), ahora, mapeador.datos(r),
                r.getOrigen().name(), r.getMetodoPagoPrevisto(), r.getVenceEn()));
        return r;
    }

    /** Cancela la reserva, libera TODOS sus turnos y deja {@code reserva.cancelada} para después del commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Cancelacion cancelar(Reserva r, CanceladoPor por, Long idAdministrador, String motivo, boolean devolucion) {
        LocalDateTime ahora = Tiempo.ahora();
        boolean hayDevolucion = devolucion && r.getMontoPagado().signum() > 0;
        BigDecimal monto = hayDevolucion ? r.getMontoPagado() : BigDecimal.ZERO.setScale(2);
        // El evento lleva lo cobrado ANTES de la devolución (pagos reembolsa con eso y notificaciones lo muestra).
        DatosReserva datos = mapeador.datos(r);
        r.setEstado(EstadoReserva.CANCELADA);
        r.setVenceEn(null);
        for (Turno t : r.turnosOrdenados()) {
            if (t.getEstado() == EstadoTurno.RESERVADO) {
                planificador.liberado(t);
            }
        }
        Cancelacion c = new Cancelacion();
        c.setReserva(r);
        c.setIdAdministrador(idAdministrador);
        c.setCanceladoPor(por);
        c.setMotivo(Texto.limpiar(motivo));
        c.setDevolucion(hayDevolucion);
        c.setMontoDevolucion(monto);
        c.setFechaCancelacion(ahora);
        cancelaciones.save(c);
        if (hayDevolucion) {
            r.registrarDevolucion(monto);
        }
        registrarHito(r, ReservaHistorial.CANCELADA, ReglasReserva.textoCancelacion(por, c.getMotivo(), monto));
        publicar(Eventos.RESERVA_CANCELADA, new ReservaCanceladaEvento(UUID.randomUUID(), ahora, datos, por.name(),
                c.getMotivo(), hayDevolucion, monto));
        return c;
    }

    /** Regla de devolución: hubo pago y faltan al menos {@code devolucionHoras} para el inicio. */
    public boolean correspondeDevolucion(Reserva r, LocalDateTime ahora) {
        long minutos = Duration.between(ahora, r.inicio()).toMinutes();
        return r.getMontoPagado().signum() > 0 && minutos >= complejo.parametros().devolucionHoras() * 60L;
    }

    public Reserva buscar(String codigo) {
        return reservas.findByCodigoReserva(normalizarCodigo(codigo))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos la reserva " + normalizarCodigo(codigo) + "."));
    }

    /** Mismo 404 si el código no existe o si el DNI no corresponde (no se revela cuál de los dos falló). */
    public Reserva buscarParaCliente(String codigo, String dni) {
        return reservas.findByCodigoReserva(normalizarCodigo(codigo))
                .filter(r -> r.getCliente().getDni().equals(dni == null ? null : dni.trim()))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(MENSAJE_NO_ENCONTRADA));
    }

    public Cancelacion cancelacionDe(Reserva r) {
        return r.getEstado() == EstadoReserva.CANCELADA ? cancelaciones.findByReservaId(r.getId()).orElse(null) : null;
    }

    public List<ReservaHistorial> historialDe(Reserva r) {
        return historial.findByIdReservaOrderByFechaAscIdAsc(r.getId());
    }

    public void registrarHito(Reserva r, String evento, String detalle) {
        historial.save(new ReservaHistorial(r.getId(), evento, detalle, Tiempo.ahora()));
    }

    /** Encola el evento; {@code PublicadorTrasCommit} lo envía a RabbitMQ cuando la transacción termina bien. */
    public void publicar(String routingKey, EventoDominio evento) {
        eventos.publishEvent(new EventoParaPublicar(routingKey, evento));
    }

    public static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    /** ALC- + 6 caracteres de un alfabeto sin I, O, 0 ni 1 (SecureRandom); se reintenta si ya existe. */
    String nuevoCodigo() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder("ALC-");
            for (int i = 0; i < 6; i++) {
                sb.append(CodigoReserva.ALFABETO.charAt(AZAR.nextInt(CodigoReserva.ALFABETO.length())));
            }
            codigo = sb.toString();
        } while (reservas.existsByCodigoReserva(codigo));
        return codigo;
    }

    /**
     * Upsert del cliente por DNI. Celular y correo se actualizan siempre. Nombres y apellidos: un cliente nuevo los toma
     * de lo escrito; uno existente los conserva, salvo en la reserva MANUAL (panel) cuando NO está verificado por
     * RENIEC: ahí recepción puede corregirlos y se guardan. Si está verificado, mandan los datos de RENIEC.
     * (En la web no se tocan los nombres de un cliente existente: cualquiera podría escribir un DNI ajeno.)
     */
    private Cliente guardarCliente(ClienteSolicitud s, LocalDateTime ahora, boolean manual) {
        String dni = s.dni().trim();
        Optional<Cliente> existente = clientes.findByDni(dni);
        Cliente c = existente.orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setDni(dni);
            nuevo.setNombres(Texto.titulo(s.nombres()));
            nuevo.setApellidos(Texto.titulo(s.apellidos()));
            nuevo.setFechaRegistro(ahora);
            return nuevo;
        });
        if (manual && existente.isPresent() && !c.isVerificadoReniec()) {
            c.setNombres(Texto.titulo(s.nombres()));
            c.setApellidos(Texto.titulo(s.apellidos()));
        }
        c.setTelefono(Texto.normalizarCelular(s.telefono()));
        c.setCorreo(Texto.correo(s.correo()));
        c.setActualizadoEn(ahora);
        if (!c.isVerificadoReniec()) {
            c.setVerificadoReniec(consultas.existsByClaveAndFuenteNot(new ConsultaDocumento.Clave(TipoDocumento.DNI, dni),
                    ConsultaDocumento.FUENTE_NO_ENCONTRADO));
        }
        return clientes.save(c);
    }

    /**
     * Valida la lista (1 a 8, sin repetir) y bloquea los turnos en orden de id. Deben ser de la misma cancha y fecha.
     *
     * @return los turnos ordenados por hora de inicio
     */
    private List<Turno> bloquearTurnos(TurnosPedidos pedidos) {
        List<Long> ids = pedidos.ids();
        String campo = pedidos.campo();
        if (ids.isEmpty() || ids.size() > Mensajes.MAX_TURNOS) {
            throw NegocioExcepcion.validacion(campo, Mensajes.TURNOS_CANTIDAD);
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw NegocioExcepcion.validacion(campo, Mensajes.TURNOS_REPETIDOS);
        }
        List<Turno> ts = new ArrayList<>(turnos.bloquearPorIds(ids));
        if (ts.size() != ids.size() || ts.stream().anyMatch(t -> !t.visible())) {
            throw NegocioExcepcion.validacion(campo, Mensajes.TURNO);
        }
        if (ts.stream().map(Turno::getIdCancha).distinct().count() > 1 || ts.stream().map(Turno::getFecha).distinct().count() > 1) {
            throw NegocioExcepcion.validacion(campo, Mensajes.TURNOS_MISMO_DIA);
        }
        ts.sort(Comparator.comparing(Turno::getHoraInicio));
        return ts;
    }

    /** Web: empieza en 30 min o más; panel: aún no termina. No bloqueado, cancha activa y sin reserva vigente. */
    private void validarLibre(Turno t, boolean web, LocalDateTime ahora, String campo) {
        boolean aTiempo = web ? !t.inicio().isBefore(ahora.plusMinutes(ReglasReserva.MINUTOS_ANTICIPACION))
                : ahora.isBefore(t.fin());
        if (!aTiempo) {
            throw NegocioExcepcion.validacion(campo, Mensajes.TURNO);
        }
        boolean canchaInactiva = complejo.cancha(t.getIdCancha()).map(c -> !c.activa()).orElse(false);
        if (t.getEstado() == EstadoTurno.BLOQUEADO || canchaInactiva) {
            throw NegocioExcepcion.conflicto("TURNO_NO_DISPONIBLE", Mensajes.TURNO);
        }
        if (t.getEstado() == EstadoTurno.RESERVADO || reservaTurnos.existsByTurnoIdAndVigenteTrue(t.getId())) {
            throw ocupado();
        }
    }

    private String textoRegistro(Reserva r, NuevaReserva n) {
        if (r.getOrigen() == Origen.WEB) {
            return "Reserva registrada por la web. Paga el adelanto de " + Dinero.formato(r.getMontoAdelanto())
                    + " antes de las " + Tiempo.horaAmPm(r.getVenceEn().toLocalTime());
        }
        String canal = r.getOrigen() == Origen.PRESENCIAL ? "en el complejo" : "por teléfono";
        String quien = n.registradoPorUsuario() == null ? "" : " por " + n.registradoPorUsuario();
        return "Reserva registrada " + canal + quien + ".";
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static NegocioExcepcion ocupado() {
        return NegocioExcepcion.conflicto("TURNO_OCUPADO", MENSAJE_OCUPADO);
    }
}
