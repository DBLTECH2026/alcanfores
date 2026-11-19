package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteResumen;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.PanelDtos.AgendaDashboardCancha;
import pe.alcanfores.reservas.dto.PanelDtos.AgendaDashboardTurno;
import pe.alcanfores.reservas.dto.PanelDtos.Alerta;
import pe.alcanfores.reservas.dto.PanelDtos.DashboardRespuesta;
import pe.alcanfores.reservas.dto.PanelDtos.Kpis;
import pe.alcanfores.reservas.dto.PanelDtos.ProximaReserva;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ClienteRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.PanelServicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PanelServicioImpl implements PanelServicio {

    private static final List<EstadoReserva> VIGENTES = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);
    private static final int MAX_ALERTAS = 15;

    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final ClienteRepositorio clientes;
    private final ComplejoServicio complejo;
    private final ReservaMapeador mapeador;

    @Override
    @Transactional(readOnly = true)
    public DashboardRespuesta dashboard(LocalDate fecha) {
        LocalDate dia = fecha == null ? Tiempo.hoy() : fecha;
        LocalDateTime ahora = Tiempo.ahora();
        List<Reserva> delDia = reservas.conTurnoEntre(dia, dia);
        List<Turno> turnosDia = turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(dia).stream().filter(Turno::visible).toList();

        long reservasHoy = delDia.stream().filter(Reserva::vigente).count();
        long confirmadasHoy = delDia.stream().filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA).count();
        long habilitados = turnosDia.stream().filter(t -> t.getEstado() != EstadoTurno.BLOQUEADO).count();
        long ocupados = turnosDia.stream().filter(t -> t.getEstado() == EstadoTurno.RESERVADO).count();

        List<Reserva> delMes = reservas.conTurnoEntre(dia.withDayOfMonth(1), dia);
        long conAsistencia = delMes.stream().filter(r -> r.getAsistencia() != Asistencia.PENDIENTE).count();
        long noShows = delMes.stream().filter(r -> r.getAsistencia() == Asistencia.NO_ASISTIO).count();

        LocalDate lunes = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long semana = reservas.conTurnoEntre(lunes, lunes.plusDays(6)).stream().filter(Reserva::vigente).count();

        Kpis kpis = new Kpis(reservasHoy, reservas.countByEstadoAndEstadoPago(EstadoReserva.PENDIENTE, EstadoPago.EN_REVISION),
                confirmadasHoy, porcentaje(ocupados, habilitados), porcentaje(noShows, conAsistencia), semana);

        List<ProximaReserva> proximas = reservas.proximasDesde(VIGENTES, ahora.toLocalDate(), ahora.toLocalTime(),
                        PageRequest.of(0, 5)).stream()
                .map(r -> new ProximaReserva(r.getCodigoReserva(), r.getCliente().nombreCompleto(), r.getTurno().getNombreCancha(),
                        r.getTurno().getFecha(), r.getTurno().getHoraInicio(), r.getEstado(), r.getEstadoPago()))
                .toList();

        return new DashboardRespuesta(dia, kpis, proximas, agenda(turnosDia, delDia), alertas(delDia, ahora, dia));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<ClienteResumen> clientes(String q, Pageable pagina) {
        String patron = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Cliente> page = clientes.buscar(patron, PageRequest.of(pagina.getPageNumber(),
                Math.min(Math.max(pagina.getPageSize(), 1), 100), Sort.by(Sort.Direction.DESC, "fechaRegistro")));
        Map<Long, List<Reserva>> porCliente = page.isEmpty() ? Map.of()
                : reservas.deClientes(page.getContent().stream().map(Cliente::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getCliente().getId()));
        return PaginaRespuesta.de(page, c -> resumen(c, porCliente.getOrDefault(c.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteDetalle cliente(String dni) {
        if (!Documentos.dniValido(dni)) {
            throw NegocioExcepcion.validacion("dni", Mensajes.DNI);
        }
        Cliente c = clientes.findByDni(dni)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos un cliente con ese DNI."));
        List<Reserva> suyas = reservas.deClientes(List.of(c.getId()));
        return new ClienteDetalle(resumen(c, suyas), suyas.stream().map(mapeador::fila).toList());
    }

    private ClienteResumen resumen(Cliente c, List<Reserva> suyas) {
        long asistidas = suyas.stream().filter(r -> r.getAsistencia() == Asistencia.ASISTIO).count();
        long noShows = suyas.stream().filter(r -> r.getAsistencia() == Asistencia.NO_ASISTIO).count();
        LocalDate ultima = suyas.stream().map(r -> r.getTurno().getFecha()).max(Comparator.naturalOrder()).orElse(null);
        return new ClienteResumen(c.getId(), c.getDni(), c.getNombres(), c.getApellidos(), c.getTelefono(), c.getCorreo(),
                c.isVerificadoReniec(), c.getFechaRegistro(), suyas.size(), asistidas, noShows, ultima);
    }

    private List<AgendaDashboardCancha> agenda(List<Turno> turnosDia, List<Reserva> delDia) {
        Map<Long, Reserva> porTurno = new HashMap<>();
        delDia.stream().filter(Reserva::vigente)
                .forEach(r -> r.turnosOrdenados().forEach(t -> porTurno.putIfAbsent(t.getId(), r)));
        Map<Long, List<Turno>> porCancha = turnosDia.stream().collect(Collectors.groupingBy(Turno::getIdCancha));
        List<AgendaDashboardCancha> agenda = new ArrayList<>();
        for (CanchaCatalogo c : complejo.canchas()) {
            List<AgendaDashboardTurno> ts = porCancha.getOrDefault(c.id(), List.of()).stream().map(t -> {
                Reserva r = porTurno.get(t.getId());
                return new AgendaDashboardTurno(t.getHoraInicio(), t.getEstado(), r == null ? null : r.getCodigoReserva(),
                        r == null ? null : r.getCliente().nombreCompleto(), r == null ? null : r.getEstadoPago());
            }).toList();
            if (c.activa() || !ts.isEmpty()) {
                agenda.add(new AgendaDashboardCancha(c.id(), c.nombre(), ts));
            }
        }
        return agenda;
    }

    private List<Alerta> alertas(List<Reserva> delDia, LocalDateTime ahora, LocalDate dia) {
        List<Alerta> alertas = new ArrayList<>();
        for (Reserva r : reservas.enEstadoYPago(EstadoReserva.PENDIENTE,
                List.of(EstadoPago.EN_REVISION, EstadoPago.OBSERVADO, EstadoPago.SIN_PAGO))) {
            String quien = r.getCliente().nombreCompleto();
            String turno = r.getTurno().getNombreCancha() + " · " + r.getTurno().getFecha().format(Tiempo.FECHA_CORTA) + " "
                    + Tiempo.horaAmPm(r.getTurno().getHoraInicio());
            if (r.getEstadoPago() == EstadoPago.EN_REVISION) {
                alertas.add(new Alerta("POR_VALIDAR", "Comprobante por validar de " + quien + " (" + turno + ")",
                        r.getCodigoReserva()));
            } else if (r.getEstadoPago() == EstadoPago.OBSERVADO) {
                alertas.add(new Alerta("OBSERVADO", quien + " debe reenviar su comprobante" + (r.getVenceEn() == null ? "."
                        : " antes de las " + Tiempo.horaAmPm(r.getVenceEn().toLocalTime())), r.getCodigoReserva()));
            } else if (r.getVenceEn() != null && r.getVenceEn().isBefore(ahora.plusMinutes(10))) {
                alertas.add(new Alerta("POR_VENCER", "Si no paga, la reserva de " + quien + " vence a las "
                        + Tiempo.horaAmPm(r.getVenceEn().toLocalTime()), r.getCodigoReserva()));
            }
        }
        if (dia.equals(ahora.toLocalDate())) {
            for (Reserva r : delDia) {
                if (r.getEstado() != EstadoReserva.CONFIRMADA) {
                    continue;
                }
                LocalDateTime inicio = r.inicio();
                if (r.saldo().signum() > 0 && !inicio.isBefore(ahora) && inicio.isBefore(ahora.plusHours(2))) {
                    alertas.add(new Alerta("SALDO_PENDIENTE", "Cobra el saldo de " + Dinero.formato(r.saldo()) + " a "
                            + r.getCliente().nombreCompleto() + " (" + Tiempo.horaAmPm(r.getTurno().getHoraInicio()) + ")",
                            r.getCodigoReserva()));
                }
                if (r.getAsistencia() == Asistencia.PENDIENTE && inicio.plusMinutes(15).isBefore(ahora)) {
                    alertas.add(new Alerta("ASISTENCIA_PENDIENTE", "Marca la asistencia de " + r.getCliente().nombreCompleto()
                            + " (" + Tiempo.horaAmPm(r.getTurno().getHoraInicio()) + ")", r.getCodigoReserva()));
                }
            }
        }
        return alertas.size() > MAX_ALERTAS ? alertas.subList(0, MAX_ALERTAS) : alertas;
    }

    static BigDecimal porcentaje(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(parte * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
