package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.dto.InternoDtos.ElegibilidadResena;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaInterna;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaSemilla;
import pe.alcanfores.reservas.dto.InternoDtos.ReservasVigentes;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaTurnoRepositorio;
import pe.alcanfores.reservas.servicio.ConstanciaServicio;
import pe.alcanfores.reservas.servicio.InternoServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InternoServicioImpl implements InternoServicio {

    private static final String NO_ENCONTRADA = "No encontramos una reserva con ese código y DNI.";

    private final ReservaRepositorio reservas;
    private final ReservaTurnoRepositorio reservaTurnos;
    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final ConstanciaServicio constancias;

    @Override
    @Transactional(readOnly = true)
    public ReservaInterna reserva(String codigo) {
        return mapeador.interna(operaciones.buscar(codigo));
    }

    @Override
    @Transactional(readOnly = true)
    public ElegibilidadResena elegibleResena(String codigo, String dni, Long idCancha) {
        Optional<Reserva> encontrada = reservas.findByCodigoReserva(OperacionesReserva.normalizarCodigo(codigo))
                .filter(r -> r.getCliente().getDni().equals(dni == null ? null : dni.trim()));
        if (encontrada.isEmpty()) {
            return new ElegibilidadResena(false, NO_ENCONTRADA, null);
        }
        Reserva r = encontrada.get();
        String autor = Texto.autorCorto(r.getCliente().getNombres(), r.getCliente().getApellidos());
        if (idCancha != null && !idCancha.equals(r.getTurno().getIdCancha())) {
            return new ElegibilidadResena(false, "Esa reserva no corresponde a esta cancha.", autor);
        }
        if (r.getEstado() != EstadoReserva.CONFIRMADA) {
            return new ElegibilidadResena(false, "Solo puedes calificar una reserva confirmada.", autor);
        }
        if (r.getAsistencia() == Asistencia.NO_ASISTIO) {
            return new ElegibilidadResena(false, "La reserva figura como no asistida.", autor);
        }
        if (!ReglasReserva.puedeResenar(r, Tiempo.ahora())) {
            return new ElegibilidadResena(false, "Podrás calificar la cancha cuando empiece tu turno.", autor);
        }
        return new ElegibilidadResena(true, null, autor);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] constancia(String codigo) {
        Reserva r = operaciones.buscar(codigo);
        return constancias.generar(r, operaciones.cancelacionDe(r));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaInterna> proximas(LocalDateTime desde, LocalDateTime hasta) {
        LocalDateTime d = desde == null ? Tiempo.ahora() : desde;
        LocalDateTime h = hasta == null ? d.plusDays(1) : hasta;
        if (h.isBefore(d)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final debe ser posterior a la inicial.");
        }
        return reservas.conTurnoEntre(d.toLocalDate(), h.toLocalDate()).stream()
                .filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA)
                .filter(r -> !r.inicio().isBefore(d) && !r.inicio().isAfter(h))
                .sorted(Comparator.comparing(Reserva::inicio))
                .map(mapeador::interna).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservasVigentes vigentes(LocalDate fecha, Collection<Long> idsCancha) {
        List<String> codigos = idsCancha == null || idsCancha.isEmpty() ? reservaTurnos.codigosVigentesEn(fecha)
                : reservaTurnos.codigosVigentesEn(fecha, idsCancha);
        return new ReservasVigentes(codigos.stream().sorted().toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaSemilla> paraSemilla() {
        return reservas.sembradas().stream().map(r -> mapeador.semilla(r, operaciones.cancelacionDe(r))).toList();
    }
}
