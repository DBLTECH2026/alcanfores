package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.CanchaDisponibilidad;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.DiaCalendario;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.DisponibilidadRespuesta;
import pe.alcanfores.reservas.dto.DisponibilidadDtos.TurnoDisponibilidad;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.modelo.EstadoTurno;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.DisponibilidadServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;
import pe.alcanfores.reservas.servicio.TurnoServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DisponibilidadServicioImpl implements DisponibilidadServicio {

    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String OCUPADO = "OCUPADO";
    public static final String BLOQUEADO = "BLOQUEADO";
    public static final String PASADO = "PASADO";

    private final TurnoRepositorio turnos;
    private final ComplejoServicio complejo;
    private final TurnoServicio turnoServicio;

    @Override
    @Transactional
    public DisponibilidadRespuesta disponibilidad(LocalDate fecha, Long idCancha) {
        ParametrosComplejo p = complejo.parametros();
        LocalDate hoy = Tiempo.hoy();
        if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(p.diasVisibles()))) {
            throw NegocioExcepcion.validacion("fecha", Mensajes.rangoFecha(p.diasVisibles()));
        }
        List<CanchaCatalogo> canchas = canchasVisibles(idCancha);
        if (!turnos.existsByFecha(fecha)) {
            // Fecha dentro de los días visibles que aún no tiene turnos: se generan al vuelo.
            turnoServicio.generarRango(fecha, fecha, List.of());
        }
        Map<Long, List<Turno>> porCancha = turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha).stream()
                .filter(Turno::visible)
                .collect(Collectors.groupingBy(Turno::getIdCancha));
        LocalDateTime limite = Tiempo.ahora().plusMinutes(ReglasReserva.MINUTOS_ANTICIPACION);
        List<CanchaDisponibilidad> resultado = new ArrayList<>();
        for (CanchaCatalogo c : canchas) {
            List<TurnoDisponibilidad> ts = porCancha.getOrDefault(c.id(), List.of()).stream()
                    .map(t -> new TurnoDisponibilidad(t.getId(), t.getHoraInicio(), t.getHoraFin(), t.getPrecio(), t.getFranja(),
                            estadoPublico(t, limite), t.isFeriado(), t.getRecargo()))
                    .toList();
            resultado.add(new CanchaDisponibilidad(c.id(), c.nombre(), c.deporte(), ts));
        }
        return new DisponibilidadRespuesta(fecha, resultado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaCalendario> calendario(LocalDate desde, Integer dias, Long idCancha) {
        ParametrosComplejo p = complejo.parametros();
        LocalDate hoy = Tiempo.hoy();
        LocalDate inicio = desde == null || desde.isBefore(hoy) ? hoy : desde;
        int cantidad = dias == null ? p.diasVisibles() + 1 : dias;
        if (cantidad < 1 || cantidad > 31) {
            throw NegocioExcepcion.validacion("dias", "Pide entre 1 y 31 días.");
        }
        LocalDate fin = inicio.plusDays(cantidad - 1L);
        LocalDate maximo = hoy.plusDays(p.diasVisibles());
        Set<Long> ids = canchasVisibles(idCancha).stream().map(CanchaCatalogo::id).collect(Collectors.toSet());
        LocalDateTime limite = Tiempo.ahora().plusMinutes(ReglasReserva.MINUTOS_ANTICIPACION);
        Map<LocalDate, List<Turno>> porFecha = turnos.findByFechaBetweenOrderByFechaAscIdCanchaAscHoraInicioAsc(inicio, fin)
                .stream().filter(t -> ids.contains(t.getIdCancha())).filter(Turno::visible)
                .collect(Collectors.groupingBy(Turno::getFecha));
        List<DiaCalendario> calendario = new ArrayList<>();
        for (LocalDate f = inicio; !f.isAfter(fin); f = f.plusDays(1)) {
            List<Turno> ts = porFecha.getOrDefault(f, List.of());
            long libres = f.isAfter(maximo) ? 0 : ts.stream().filter(t -> DISPONIBLE.equals(estadoPublico(t, limite))).count();
            calendario.add(new DiaCalendario(f, libres, ts.size()));
        }
        return calendario;
    }

    private List<CanchaCatalogo> canchasVisibles(Long idCancha) {
        if (idCancha != null) {
            CanchaCatalogo c = complejo.cancha(idCancha)
                    .orElseThrow(() -> NegocioExcepcion.noEncontrado("Esa cancha no existe."));
            return c.activa() ? List.of(c) : List.of();
        }
        return complejo.canchas().stream().filter(CanchaCatalogo::activa).toList();
    }

    /** DISPONIBLE | OCUPADO (reserva vigente) | BLOQUEADO | PASADO (empieza en menos de 30 minutos). */
    static String estadoPublico(Turno t, LocalDateTime limite) {
        if (t.getEstado() == EstadoTurno.RESERVADO) {
            return OCUPADO;
        }
        if (t.getEstado() == EstadoTurno.BLOQUEADO) {
            return BLOQUEADO;
        }
        return t.inicio().isBefore(limite) ? PASADO : DISPONIBLE;
    }
}
