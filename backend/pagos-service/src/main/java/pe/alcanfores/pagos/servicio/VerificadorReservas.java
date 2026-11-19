package pe.alcanfores.pagos.servicio;

import org.springframework.stereotype.Component;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.cliente.ReservasCliente;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ConceptoReserva;

import java.util.Locale;
import java.util.Set;

/** Reglas comunes para aceptar un pago de una reserva (consulta a reservas-service + estado local de pagos). */
@Component
public class VerificadorReservas {

    static final String NO_ENCONTRADA = "No encontramos una reserva con ese código y DNI.";
    private static final Set<String> ESTADOS_PAGO_ABIERTOS = Set.of("SIN_PAGO", "OBSERVADO");

    private final ReservasCliente reservas;
    private final PagoRepositorio pagos;

    public VerificadorReservas(ReservasCliente reservas, PagoRepositorio pagos) {
        this.reservas = reservas;
        this.pagos = pagos;
    }

    /** Para Yape/Plin y pagos en línea: el DNI debe coincidir y la reserva debe estar PENDIENTE sin pago en revisión. */
    public ReservaInterna paraPagoDelCliente(String codigoReserva, String dni) {
        String codigo = normalizar(codigoReserva);
        ReservaInterna r = reservas.buscar(codigo)
                .filter(x -> dni != null && dni.trim().equals(x.dniCliente()))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADA));
        if (ReservaInterna.CANCELADA.equals(r.estado())) {
            throw NegocioExcepcion.regla("RESERVA_CANCELADA", "Esta reserva fue cancelada; ya no se puede pagar.");
        }
        if (!ReservaInterna.PENDIENTE.equals(r.estado())
                || pagos.existsByCodigoReservaAndEstado(codigo, EstadoValidacion.VALIDADO)) {
            throw NegocioExcepcion.conflicto("RESERVA_YA_PAGADA",
                    "Esta reserva ya tiene su pago confirmado. El saldo se paga en el complejo.");
        }
        if (!ESTADOS_PAGO_ABIERTOS.contains(r.estadoPago())
                || pagos.existsByCodigoReservaAndEstado(codigo, EstadoValidacion.PENDIENTE)) {
            throw NegocioExcepcion.conflicto("PAGO_EN_REVISION",
                    "Ya recibimos un comprobante para esta reserva y lo estamos validando. Te avisaremos en unos minutos.");
        }
        if (r.inicio() != null && !r.inicio().isAfter(Tiempo.ahora())) {
            throw NegocioExcepcion.regla("RESERVA_INICIADA", "La hora de la reserva ya pasó; ya no se puede pagar en línea.");
        }
        return r;
    }

    /** Para la caja del panel: basta con que exista. */
    public ReservaInterna paraCaja(String codigoReserva) {
        return reservas.buscar(normalizar(codigoReserva))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos la reserva " + normalizar(codigoReserva) + "."));
    }

    public static String normalizar(String codigoReserva) {
        return codigoReserva == null ? null : codigoReserva.trim().toUpperCase(Locale.ROOT);
    }

    /** Fotografía de la reserva que se guarda con el pago. */
    public static InstantaneaReserva instantanea(ReservaInterna r) {
        ClienteContacto c = r.cliente();
        return new InstantaneaReserva(c.dni(), c.nombres(), c.apellidos(), c.telefono(), c.correo(),
                ConceptoReserva.concepto(r.nombreCancha(), r.deporte(), r.fecha(), r.horario(), r.turnos(),
                        r.codigoReserva()),
                ConceptoReserva.porcentaje(r.montoAdelanto(), r.montoTotal()));
    }
}
