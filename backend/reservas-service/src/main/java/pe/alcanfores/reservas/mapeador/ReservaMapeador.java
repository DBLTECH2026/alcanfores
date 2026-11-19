package pe.alcanfores.reservas.mapeador;

import org.springframework.stereotype.Component;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteCompleto;
import pe.alcanfores.reservas.dto.AdminDtos.ClienteFila;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaAdminDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaFila;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaInterna;
import pe.alcanfores.reservas.dto.InternoDtos.ReservaSemilla;
import pe.alcanfores.reservas.dto.ReservaDtos.CanchaResumen;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionInfo;
import pe.alcanfores.reservas.dto.ReservaDtos.ClientePublico;
import pe.alcanfores.reservas.dto.ReservaDtos.EventoLinea;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteItem;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.dto.ReservaDtos.TurnoReserva;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.Cliente;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ReglasReserva;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Convierte entidades en DTO y en los datos que viajan en los eventos. En todas las respuestas {@code horaInicio} es la
 * del primer turno, {@code horaFin} la del último y {@code turnos} trae cada turno con su precio.
 */
@Component
public class ReservaMapeador {

    public ClienteContacto contacto(Cliente c) {
        return new ClienteContacto(c.getDni(), c.getNombres(), c.getApellidos(), c.getTelefono(), c.getCorreo());
    }

    public DatosReserva datos(Reserva r) {
        Turno t = r.getTurno();
        return new DatosReserva(r.getId(), r.getCodigoReserva(), t.getIdCancha(), t.getNombreCancha(), t.getDeporte(),
                t.getFecha(), r.horaInicio(), r.horaFin(), r.getMontoTotal(), r.getMontoAdelanto(), r.getMontoPagado(),
                contacto(r.getCliente()), r.horarioTexto(), r.cantidadTurnos());
    }

    /** Turnos de la reserva con su precio (si no tiene filas en reserva_turno, el primer turno con el total). */
    public List<TurnoReserva> turnos(Reserva r) {
        if (r.getTurnos().isEmpty()) {
            Turno t = r.getTurno();
            return List.of(new TurnoReserva(t.getId(), t.getHoraInicio(), t.getHoraFin(), r.getMontoTotal()));
        }
        return r.turnosConPrecio().stream().map(rt -> new TurnoReserva(rt.getTurno().getId(), rt.getTurno().getHoraInicio(),
                rt.getTurno().getHoraFin(), rt.getPrecio())).toList();
    }

    public CanchaResumen cancha(Turno t) {
        return new CanchaResumen(t.getIdCancha(), t.getNombreCancha(), t.getDeporte());
    }

    public ClientePublico clientePublico(Cliente c) {
        return new ClientePublico(c.getNombres(), c.getApellidos(), Documentos.enmascararDni(c.getDni()), c.getTelefono(),
                c.getCorreo());
    }

    public ReservaCreadaRespuesta creada(Reserva r) {
        Turno t = r.getTurno();
        return new ReservaCreadaRespuesta(r.getCodigoReserva(), r.getEstado(), r.getEstadoPago(), r.getVenceEn(), cancha(t),
                t.getFecha(), r.horaInicio(), r.horaFin(), r.getMontoTotal(), r.getMontoAdelanto(),
                clientePublico(r.getCliente()), turnos(r), r.horarioTexto(), r.cantidadTurnos());
    }

    public CancelacionInfo cancelacion(Cancelacion c) {
        return c == null ? null : new CancelacionInfo(c.getCanceladoPor().name(), c.getMotivo(), c.getFechaCancelacion(),
                c.isDevolucion(), c.getMontoDevolucion());
    }

    public List<EventoLinea> linea(List<ReservaHistorial> historial) {
        return historial.stream().map(h -> new EventoLinea(h.getEvento(), h.getFecha(), h.getDetalle())).toList();
    }

    /** {@code yaResenada}: catálogo ya tiene la reseña de esta reserva (entonces no se ofrece «Calificar»). */
    public ReservaClienteDetalle detalleCliente(Reserva r, Cancelacion c, List<ReservaHistorial> historial, LocalDateTime ahora,
                                                boolean yaResenada) {
        Turno t = r.getTurno();
        return new ReservaClienteDetalle(r.getCodigoReserva(), r.getEstado(), r.getEstadoPago(), r.getVenceEn(), cancha(t),
                t.getFecha(), r.horaInicio(), r.horaFin(), r.getMontoTotal(), r.getMontoAdelanto(),
                clientePublico(r.getCliente()), r.getAsistencia(), r.getMontoPagado(), r.saldo(), r.getMetodoPagoPrevisto(),
                cancelacion(c), linea(historial), ReglasReserva.puedeCancelarCliente(r, ahora),
                ReglasReserva.puedeReenviarComprobante(r, ahora), !yaResenada && ReglasReserva.puedeResenar(r, ahora),
                turnos(r), r.horarioTexto(), r.cantidadTurnos(), yaResenada);
    }

    /** Ítem de «Mis reservas». */
    public ReservaClienteItem itemCliente(Reserva r, LocalDateTime ahora, boolean yaResenada) {
        Turno t = r.getTurno();
        return new ReservaClienteItem(r.getCodigoReserva(), r.getEstado(), r.getEstadoPago(), r.getAsistencia(), r.getOrigen(),
                cancha(t), t.getFecha(), r.horaInicio(), r.horaFin(), r.horarioTexto(), r.cantidadTurnos(), turnos(r),
                r.getMontoTotal(), r.getMontoAdelanto(), r.getMontoPagado(), r.saldo(), r.getVenceEn(), r.getFechaRegistro(),
                ReglasReserva.puedeCancelarCliente(r, ahora), ReglasReserva.puedeReenviarComprobante(r, ahora),
                !yaResenada && ReglasReserva.puedeResenar(r, ahora), yaResenada);
    }

    public ReservaAdminDetalle detalleAdmin(Reserva r, Cancelacion c, List<ReservaHistorial> historial, LocalDateTime ahora) {
        Turno t = r.getTurno();
        Cliente cl = r.getCliente();
        return new ReservaAdminDetalle(r.getCodigoReserva(), r.getEstado(), r.getEstadoPago(), r.getAsistencia(), r.getOrigen(),
                r.getMetodoPagoPrevisto(), cancha(t), t.getId(), t.getFecha(), r.horaInicio(), r.horaFin(), t.getFranja(),
                r.getMontoTotal(), r.getMontoAdelanto(), r.getMontoPagado(), r.saldo(), r.getVenceEn(), r.getFechaRegistro(),
                r.getConfirmadaEn(), r.getRegistradoPor(), r.getNota(),
                new ClienteCompleto(cl.getId(), cl.getDni(), cl.getNombres(), cl.getApellidos(), cl.getTelefono(),
                        cl.getCorreo(), cl.isVerificadoReniec()),
                cancelacion(c), linea(historial), ReglasReserva.puedeCancelarAdmin(r),
                ReglasReserva.puedeMarcarAsistencia(r, ahora), ReglasReserva.puedeCobrarSaldo(r), turnos(r), r.horarioTexto(),
                r.cantidadTurnos());
    }

    public ReservaFila fila(Reserva r) {
        Turno t = r.getTurno();
        Cliente c = r.getCliente();
        return new ReservaFila(r.getCodigoReserva(), new ClienteFila(c.getNombres(), c.getApellidos(), c.getDni(), c.getTelefono()),
                t.getNombreCancha(), t.getFecha(), r.horaInicio(), r.horaFin(), r.getEstado(), r.getEstadoPago(),
                r.getAsistencia(), r.getOrigen(), r.getMontoTotal(), r.getMontoPagado(), r.getFechaRegistro(), turnos(r),
                r.horarioTexto(), r.cantidadTurnos());
    }

    public ReservaInterna interna(Reserva r) {
        Turno t = r.getTurno();
        return new ReservaInterna(r.getId(), r.getCodigoReserva(), t.getIdCancha(), t.getNombreCancha(), t.getDeporte(),
                t.getFecha(), r.horaInicio(), r.horaFin(), r.getMontoTotal(), r.getMontoAdelanto(), r.getMontoPagado(),
                contacto(r.getCliente()), r.getEstado(), r.getEstadoPago(), r.getAsistencia(), r.getOrigen(),
                r.getMetodoPagoPrevisto(), r.saldo(), r.inicio(), r.getVenceEn(), r.horarioTexto(), r.cantidadTurnos(),
                turnos(r));
    }

    public ReservaSemilla semilla(Reserva r, Cancelacion c) {
        Turno t = r.getTurno();
        return new ReservaSemilla(r.getCodigoReserva(), r.getEstado(), r.getEstadoPago(), r.getMetodoPagoPrevisto(),
                r.getMontoTotal(), r.getMontoAdelanto(), r.getMontoPagado(), r.getFechaRegistro(), r.getConfirmadaEn(),
                r.getOrigen(), t.getNombreCancha(), t.getFecha(), r.horaInicio(), contacto(r.getCliente()),
                c != null && c.isDevolucion() ? c.getMontoDevolucion() : null);
    }
}
