package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.cliente.CatalogoCliente;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.servicio.ConstanciaServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;
import pe.alcanfores.reservas.servicio.ReservaServicio;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservaServicioImpl implements ReservaServicio {

    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final ConstanciaServicio constancias;
    private final Auditor auditor;
    private final CatalogoCliente catalogo;

    @Override
    @Transactional
    public ReservaCreadaRespuesta registrar(RegistroReservaSolicitud s) {
        RegistroReservaSolicitud solicitud = conDniDeLaCuenta(s);
        Reserva r = operaciones.crear(new OperacionesReserva.NuevaReserva(solicitud.turnos(), solicitud.cliente(), Origen.WEB,
                solicitud.metodoPago(), null, null, null));
        return mapeador.creada(r);
    }

    /** Con sesión de cliente (token CLIENTE) se reserva SIEMPRE a nombre del DNI de la cuenta. */
    static RegistroReservaSolicitud conDniDeLaCuenta(RegistroReservaSolicitud s) {
        return SeguridadUtil.actual().filter(UsuarioToken::esCliente).map(UsuarioToken::dni)
                .filter(dni -> dni != null && !dni.isBlank() && s.cliente() != null)
                .map(dni -> s.conCliente(new ClienteSolicitud(dni, s.cliente().nombres(), s.cliente().apellidos(),
                        s.cliente().telefono(), s.cliente().correo())))
                .orElse(s);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaClienteDetalle consultar(String codigo, String dni) {
        Reserva r = operaciones.buscarParaCliente(codigo, dni);
        return detalle(r, Tiempo.ahora());
    }

    @Override
    @Transactional
    public ReservaClienteDetalle cancelar(String codigo, CancelacionClienteSolicitud s) {
        Reserva r = operaciones.buscarParaCliente(codigo, s.dni());
        LocalDateTime ahora = Tiempo.ahora();
        if (!r.vigente()) {
            throw NegocioExcepcion.regla("RESERVA_NO_CANCELABLE", "Esta reserva ya estaba cancelada.");
        }
        if (!ahora.isBefore(r.inicio())) {
            throw NegocioExcepcion.regla("RESERVA_YA_EMPEZO",
                    "Tu turno ya empezó; para cualquier cambio comunícate con el complejo por WhatsApp.");
        }
        boolean devolucion = operaciones.correspondeDevolucion(r, ahora);
        operaciones.cancelar(r, CanceladoPor.CLIENTE, null, s.motivo(), devolucion);
        auditor.registrarComo("Cliente " + Documentos.enmascararDni(r.getCliente().getDni()), "CLIENTE", "CANCELAR_RESERVA",
                "reserva", r.getCodigoReserva(), "Cancelada por el cliente" + (devolucion ? " con devolución" : ""));
        return detalle(r, ahora);
    }

    /** Solo si la reserva ya permite reseñar se pregunta a catálogo si ya la tiene (evita una llamada en los demás casos). */
    private ReservaClienteDetalle detalle(Reserva r, LocalDateTime ahora) {
        boolean yaResenada = ReglasReserva.puedeResenar(r, ahora) && catalogo.tieneResena(r.getCodigoReserva());
        return mapeador.detalleCliente(r, operaciones.cancelacionDe(r), operaciones.historialDe(r), ahora, yaResenada);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] constancia(String codigo, String dni) {
        Reserva r = operaciones.buscarParaCliente(codigo, dni);
        return constancias.generar(r, operaciones.cancelacionDe(r));
    }
}
