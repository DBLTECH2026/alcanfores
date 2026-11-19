package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.dto.PanelDtos.Cancelaciones;
import pe.alcanfores.reservas.dto.PanelDtos.MotivoFrecuente;
import pe.alcanfores.reservas.dto.PanelDtos.PorCancha;
import pe.alcanfores.reservas.dto.PanelDtos.PorDia;
import pe.alcanfores.reservas.dto.PanelDtos.PorHora;
import pe.alcanfores.reservas.dto.PanelDtos.PorOrigen;
import pe.alcanfores.reservas.dto.PanelDtos.ReporteReservas;
import pe.alcanfores.reservas.dto.PanelDtos.TopCliente;
import pe.alcanfores.reservas.dto.PanelDtos.Totales;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.CancelacionRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.ReporteServicio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static pe.alcanfores.reservas.servicio.impl.PanelServicioImpl.porcentaje;

@Service
@RequiredArgsConstructor
public class ReporteServicioImpl implements ReporteServicio {

    private static final int DIAS_POR_DEFECTO = 30;
    private static final int DIAS_MAXIMOS = 366;

    private final ReservaRepositorio reservas;
    private final TurnoRepositorio turnos;
    private final CancelacionRepositorio cancelaciones;
    private final ComplejoServicio complejo;
    private final ReservaMapeador mapeador;
    private final ReporteExcel excel;

    @Override
    @Transactional(readOnly = true)
    public ReporteReservas reporte(LocalDate desde, LocalDate hasta) {
        Rango rango = rango(desde, hasta);
        return calcular(rango, reservas.conTurnoEntre(rango.desde(), rango.hasta()));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] excel(LocalDate desde, LocalDate hasta) {
        Rango rango = rango(desde, hasta);
        List<Reserva> lista = new ArrayList<>(reservas.conTurnoEntre(rango.desde(), rango.hasta()));
        lista.sort(Comparator.comparing(Reserva::inicio));
        return excel.generar(calcular(rango, lista), lista.stream().map(mapeador::fila).toList());
    }

    private ReporteReservas calcular(Rango rango, List<Reserva> lista) {
        List<Turno> turnosRango = turnos.findByFechaBetweenOrderByFechaAscIdCanchaAscHoraInicioAsc(rango.desde(), rango.hasta());
        List<Reserva> vigentes = lista.stream().filter(Reserva::vigente).toList();
        long habilitados = turnosRango.stream().filter(t -> t.getEstado() != EstadoTurno.BLOQUEADO).count();
        long asistidas = contar(lista, r -> r.getAsistencia() == Asistencia.ASISTIO);
        long noAsistio = contar(lista, r -> r.getAsistencia() == Asistencia.NO_ASISTIO);
        Totales totales = new Totales(lista.size(), contar(lista, r -> r.getEstado() == EstadoReserva.CONFIRMADA),
                contar(lista, r -> r.getEstado() == EstadoReserva.CANCELADA),
                contar(lista, r -> r.getEstado() == EstadoReserva.PENDIENTE), asistidas, noAsistio,
                porcentaje(noAsistio, asistidas + noAsistio), porcentaje(vigentes.size(), habilitados));

        List<PorCancha> porCancha = new ArrayList<>();
        for (CanchaCatalogo c : complejo.canchas()) {
            long reservasCancha = contar(vigentes, r -> c.id().equals(r.getTurno().getIdCancha()));
            long turnosCancha = turnosRango.stream()
                    .filter(t -> c.id().equals(t.getIdCancha()) && t.getEstado() != EstadoTurno.BLOQUEADO).count();
            porCancha.add(new PorCancha(c.id(), c.nombre(), reservasCancha, porcentaje(reservasCancha, turnosCancha)));
        }

        List<PorDia> porDia = new ArrayList<>();
        Map<LocalDate, List<Reserva>> agrupadasDia = lista.stream().collect(Collectors.groupingBy(r -> r.getTurno().getFecha()));
        for (LocalDate f = rango.desde(); !f.isAfter(rango.hasta()); f = f.plusDays(1)) {
            List<Reserva> delDia = agrupadasDia.getOrDefault(f, List.of());
            porDia.add(new PorDia(f, delDia.size(), contar(delDia, r -> r.getEstado() == EstadoReserva.CONFIRMADA),
                    contar(delDia, r -> r.getEstado() == EstadoReserva.CANCELADA)));
        }

        Map<LocalTime, Long> porHoraMapa = new TreeMap<>();
        complejo.parametros().horasDeInicio().forEach(h -> porHoraMapa.put(h, 0L));
        vigentes.forEach(r -> porHoraMapa.merge(r.getTurno().getHoraInicio(), 1L, Long::sum));
        List<PorHora> porHora = porHoraMapa.entrySet().stream()
                .map(e -> new PorHora(e.getKey().format(Tiempo.HORA), e.getValue())).toList();

        List<PorOrigen> porOrigen = new ArrayList<>();
        for (Origen o : Origen.values()) {
            porOrigen.add(new PorOrigen(o.name(), contar(lista, r -> r.getOrigen() == o)));
        }

        List<Long> idsCanceladas = lista.stream().filter(r -> r.getEstado() == EstadoReserva.CANCELADA).map(Reserva::getId).toList();
        List<Cancelacion> canceladas = idsCanceladas.isEmpty() ? List.of() : cancelaciones.deReservas(idsCanceladas);
        Map<String, Long> porActor = new LinkedHashMap<>();
        for (CanceladoPor actor : CanceladoPor.values()) {
            porActor.put(actor.name(), canceladas.stream().filter(c -> c.getCanceladoPor() == actor).count());
        }
        List<MotivoFrecuente> motivos = canceladas.stream()
                .collect(Collectors.groupingBy(c -> Texto.limpiar(c.getMotivo()), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5).map(e -> new MotivoFrecuente(e.getKey(), e.getValue())).toList();

        Map<Cliente, List<Reserva>> porCliente = vigentes.stream().collect(Collectors.groupingBy(Reserva::getCliente));
        List<TopCliente> top = porCliente.entrySet().stream()
                .map(e -> new TopCliente(e.getKey().getDni(), e.getKey().nombreCompleto(), e.getValue().size(),
                        contar(e.getValue(), r -> r.getAsistencia() == Asistencia.ASISTIO)))
                .sorted(Comparator.comparingLong(TopCliente::reservas).reversed()
                        .thenComparing(Comparator.comparingLong(TopCliente::asistidas).reversed())
                        .thenComparing(TopCliente::nombre))
                .limit(5).toList();

        return new ReporteReservas(rango.desde(), rango.hasta(), totales, porCancha, porDia, porHora, porOrigen,
                new Cancelaciones(porActor, motivos), top);
    }

    private static Rango rango(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta == null ? Tiempo.hoy() : hasta;
        LocalDate d = desde == null ? h.minusDays(DIAS_POR_DEFECTO - 1L) : desde;
        if (h.isBefore(d)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(d, h) >= DIAS_MAXIMOS) {
            throw NegocioExcepcion.validacion("hasta", "El reporte abarca como máximo " + DIAS_MAXIMOS + " días.");
        }
        return new Rango(d, h);
    }

    private static long contar(List<Reserva> lista, Predicate<Reserva> condicion) {
        return lista.stream().filter(condicion).count();
    }

    private record Rango(LocalDate desde, LocalDate hasta) {
    }
}
