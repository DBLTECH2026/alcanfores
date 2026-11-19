package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.ReservaDtos.ClienteSolicitud;
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
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private static final List<EstadoReserva> VIGENTES = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);
    private static final SecureRandom AZAR = new SecureRandom();

    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final ClienteRepositorio clientes;
    private final CancelacionRepositorio cancelaciones;
    private final HistorialRepositorio historial;
    private final ConsultaDocumentoRepositorio consultas;
    private final ComplejoServicio complejo;
    private final ReservaMapeador mapeador;
    private final ApplicationEventPublisher eventos;

    /** Datos para crear una reserva (web o manual). */
    public record NuevaReserva(Long idTurno, ClienteSolicitud cliente, Origen origen, String metodoPagoPrevisto, String nota,
                               Long registradoPor, String registradoPorUsuario) {
    }

    /**
     * Crea la reserva PENDIENTE/SIN_PAGO: bloquea el turno con {@code SELECT … FOR UPDATE}, valida las reglas del §3,
     * hace upsert del cliente por DNI y deja el evento {@code reserva.registrada} para después del commit.
     * Si aun así dos reservas chocan, el índice UNIQUE de {@code turno_activo} lo impide → 409 TURNO_OCUPADO.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reserva crear(NuevaReserva n) {
        ParametrosComplejo p = complejo.parametros();
        LocalDateTime ahora = Tiempo.ahora();
        LocalDate hoy = ahora.toLocalDate();
        boolean web = n.origen() == Origen.WEB;

        Turno t = turnos.bloquearPorId(n.idTurno())
                .orElseThrow(() -> NegocioExcepcion.validacion("idTurno", Mensajes.TURNO));
        if (t.getFecha().isBefore(hoy) || t.getFecha().isAfter(hoy.plusDays(p.diasVisibles()))) {
            throw NegocioExcepcion.validacion("fecha", Mensajes.rangoFecha(p.diasVisibles()));
        }
        boolean aTiempo = web ? !t.inicio().isBefore(ahora.plusMinutes(ReglasReserva.MINUTOS_ANTICIPACION))
                : ahora.isBefore(t.fin());
        if (!aTiempo) {
            throw NegocioExcepcion.validacion("idTurno", Mensajes.TURNO);
        }
        boolean canchaInactiva = complejo.cancha(t.getIdCancha()).map(c -> !c.activa()).orElse(false);
        if (t.getEstado() == EstadoTurno.BLOQUEADO || canchaInactiva) {
            throw NegocioExcepcion.conflicto("TURNO_NO_DISPONIBLE", Mensajes.TURNO);
        }
        if (t.getEstado() == EstadoTurno.RESERVADO || !reservas.deTurnoEnEstados(t.getId(), VIGENTES).isEmpty()) {
            throw ocupado();
        }

        Reserva r = new Reserva();
        r.setCodigoReserva(nuevoCodigo());
        r.setCliente(guardarCliente(n.cliente(), ahora));
        r.setTurno(t);
        r.setEstado(EstadoReserva.PENDIENTE);
        r.setEstadoPago(EstadoPago.SIN_PAGO);
        r.setOrigen(n.origen());
        r.setMetodoPagoPrevisto(n.metodoPagoPrevisto());
        r.setMontoTotal(Dinero.redondear(t.getPrecio()));
        r.setMontoAdelanto(Dinero.porcentaje(t.getPrecio(), p.adelantoPct()));
        r.setMontoPagado(BigDecimal.ZERO.setScale(2));
        r.setVenceEn(web ? min(ahora.plusMinutes(p.minutosParaPagar()), t.inicio()) : null);
        r.setFechaRegistro(ahora);
        r.setRegistradoPor(n.registradoPor());
        r.setNota(n.nota() == null || n.nota().isBlank() ? null : Texto.limpiar(n.nota()));
        t.setEstado(EstadoTurno.RESERVADO);
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

    /** Cancela la reserva, libera el turno y deja {@code reserva.cancelada} para después del commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Cancelacion cancelar(Reserva r, CanceladoPor por, Long idAdministrador, String motivo, boolean devolucion) {
        LocalDateTime ahora = Tiempo.ahora();
        boolean hayDevolucion = devolucion && r.getMontoPagado().signum() > 0;
        BigDecimal monto = hayDevolucion ? r.getMontoPagado() : BigDecimal.ZERO.setScale(2);
        r.setEstado(EstadoReserva.CANCELADA);
        r.setVenceEn(null);
        Turno t = r.getTurno();
        if (t.getEstado() == EstadoTurno.RESERVADO) {
            t.setEstado(EstadoTurno.DISPONIBLE);
            complejo.cancha(t.getIdCancha()).filter(CanchaCatalogo::activa).ifPresent(c -> t.setPrecio(c.precio(t.getFranja())));
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
        String quien = switch (por) {
            case CLIENTE -> "Cancelada por el cliente";
            case ADMINISTRADOR -> "Cancelada por el complejo";
            case SISTEMA -> "Cancelada por el sistema";
        };
        registrarHito(r, ReservaHistorial.CANCELADA, quien + ": " + c.getMotivo()
                + (hayDevolucion ? ". Devolución de " + Dinero.formato(monto) + "." : "."));
        publicar(Eventos.RESERVA_CANCELADA, new ReservaCanceladaEvento(UUID.randomUUID(), ahora, mapeador.datos(r), por.name(),
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

    private Cliente guardarCliente(ClienteSolicitud s, LocalDateTime ahora) {
        String dni = s.dni().trim();
        Cliente c = clientes.findByDni(dni).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setDni(dni);
            nuevo.setNombres(Texto.titulo(s.nombres()));
            nuevo.setApellidos(Texto.titulo(s.apellidos()));
            nuevo.setFechaRegistro(ahora);
            return nuevo;
        });
        c.setTelefono(Texto.normalizarCelular(s.telefono()));
        c.setCorreo(Texto.correo(s.correo()));
        c.setActualizadoEn(ahora);
        if (!c.isVerificadoReniec()) {
            c.setVerificadoReniec(consultas.existsById(new ConsultaDocumento.Clave(TipoDocumento.DNI, dni)));
        }
        return clientes.save(c);
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
