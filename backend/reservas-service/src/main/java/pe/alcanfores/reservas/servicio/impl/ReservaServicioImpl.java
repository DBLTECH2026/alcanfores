package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.dto.ReservaDtos.CancelacionClienteSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.RegistroReservaSolicitud;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaClienteDetalle;
import pe.alcanfores.reservas.dto.ReservaDtos.ReservaCreadaRespuesta;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.servicio.ConstanciaServicio;
import pe.alcanfores.reservas.servicio.ReservaServicio;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservaServicioImpl implements ReservaServicio {

    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final ConstanciaServicio constancias;
    private final Auditor auditor;

    @Override
    @Transactional
    public ReservaCreadaRespuesta registrar(RegistroReservaSolicitud s) {
        Reserva r = operaciones.crear(new OperacionesReserva.NuevaReserva(s.idTurno(), s.cliente(), Origen.WEB,
                s.metodoPago(), null, null, null));
        return mapeador.creada(r);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaClienteDetalle consultar(String codigo, String dni) {
        Reserva r = operaciones.buscarParaCliente(codigo, dni);
        return mapeador.detalleCliente(r, operaciones.cancelacionDe(r), operaciones.historialDe(r), Tiempo.ahora());
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
        return mapeador.detalleCliente(r, operaciones.cancelacionDe(r), operaciones.historialDe(r), ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] constancia(String codigo, String dni) {
        Reserva r = operaciones.buscarParaCliente(codigo, dni);
        return constancias.generar(r, operaciones.cancelacionDe(r));
    }
}
