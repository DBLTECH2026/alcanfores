package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CatalogoCliente;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteItem;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.repositorio.CancelacionRepositorio;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.servicio.MisReservasServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MisReservasServicioImpl implements MisReservasServicio {

    private static final int TAMANIO_MAXIMO = 50;

    private final ReservaRepositorio reservas;
    private final CancelacionRepositorio cancelaciones;
    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final CatalogoCliente catalogo;

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<ReservaClienteItem> listar(String dni, String estado, Pageable pagina) {
        EstadoReserva filtro = estado(estado);
        PageRequest orden = PageRequest.of(Math.max(pagina.getPageNumber(), 0),
                Math.min(Math.max(pagina.getPageSize(), 1), TAMANIO_MAXIMO),
                Sort.by(Sort.Direction.DESC, "turno.fecha", "turno.horaInicio", "fechaRegistro"));
        Page<Reserva> page = reservas.deDni(dni, filtro, orden);
        LocalDateTime ahora = Tiempo.ahora();
        Map<Long, Cancelacion> canceladas = cancelacionesDe(page.getContent());
        // Solo se pregunta a catálogo por las que ya serían reseñables (las demás no pueden tener reseña).
        return PaginaRespuesta.de(page, r -> mapeador.itemCliente(r, canceladas.get(r.getId()), ahora, yaResenada(r, ahora)));
    }

    /** Cancelaciones de las reservas canceladas de la página, en una sola consulta (por id de reserva). */
    private Map<Long, Cancelacion> cancelacionesDe(List<Reserva> pagina) {
        List<Long> ids = pagina.stream().filter(r -> r.getEstado() == EstadoReserva.CANCELADA).map(Reserva::getId).toList();
        return ids.isEmpty() ? Map.of() : cancelaciones.deReservas(ids).stream()
                .collect(Collectors.toMap(c -> c.getReserva().getId(), Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaClienteDetalle detalle(String dni, String codigo) {
        Reserva r = operaciones.buscarParaCliente(codigo, dni);
        LocalDateTime ahora = Tiempo.ahora();
        return mapeador.detalleCliente(r, operaciones.cancelacionDe(r), operaciones.historialDe(r), ahora, yaResenada(r, ahora),
                operaciones.reprogramacionesDe(r));
    }

    private boolean yaResenada(Reserva r, LocalDateTime ahora) {
        return ReglasReserva.puedeResenar(r, ahora) && catalogo.tieneResena(r.getCodigoReserva());
    }

    private static EstadoReserva estado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return EstadoReserva.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw NegocioExcepcion.validacion("estado", Mensajes.ESTADO);
        }
    }
}
