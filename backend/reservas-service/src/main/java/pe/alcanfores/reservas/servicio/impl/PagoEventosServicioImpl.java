package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.evento.IdempotenciaEventos;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.PagoEventosServicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PagoEventosServicioImpl implements PagoEventosServicio {

    private static final Logger log = LoggerFactory.getLogger(PagoEventosServicioImpl.class);
    private static final int HORAS_PARA_REENVIAR = 12;

    private final ReservaRepositorio reservas;
    private final IdempotenciaEventos idempotencia;
    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final ComplejoServicio complejo;

    @Override
    @Transactional
    public void alRegistrarPago(PagoRegistradoEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.PAGO_REGISTRADO)) {
            return;
        }
        Optional<Reserva> encontrada = buscar(e.codigoReserva(), Eventos.PAGO_REGISTRADO);
        if (encontrada.isEmpty()) {
            return;
        }
        Reserva r = encontrada.get();
        if (r.getEstado() == EstadoReserva.PENDIENTE
                && (r.getEstadoPago() == EstadoPago.SIN_PAGO || r.getEstadoPago() == EstadoPago.OBSERVADO)) {
            r.setEstadoPago(EstadoPago.EN_REVISION);
            r.setVenceEn(null);
        }
        operaciones.registrarHito(r, ReservaHistorial.COMPROBANTE_RECIBIDO, "Recibimos tu comprobante " + texto(e.metodo())
                + " por " + Dinero.formato(e.monto()) + "; lo estamos validando.");
    }

    @Override
    @Transactional
    public void alValidarPago(PagoValidadoEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.PAGO_VALIDADO)) {
            return;
        }
        Optional<Reserva> encontrada = buscar(e.codigoReserva(), Eventos.PAGO_VALIDADO);
        if (encontrada.isEmpty()) {
            return;
        }
        Reserva r = encontrada.get();
        LocalDateTime ahora = Tiempo.ahora();
        BigDecimal pagado = Dinero.redondear(r.getMontoPagado().add(e.monto()));
        r.setMontoPagado(pagado);
        boolean completo = pagado.compareTo(r.getMontoTotal()) >= 0;
        String tipo = e.tipoPago() == null ? "" : e.tipoPago();
        r.setEstadoPago(completo || "TOTAL".equals(tipo) ? EstadoPago.PAGADO_TOTAL : EstadoPago.ADELANTO_PAGADO);
        r.setVenceEn(null);
        operaciones.registrarHito(r, ReservaHistorial.PAGO_VALIDADO, "Pago " + switch (tipo) {
            case "TOTAL" -> "total";
            case "SALDO" -> "del saldo";
            default -> "del adelanto";
        } + " validado: " + Dinero.formato(e.monto()) + " (" + texto(e.metodo()) + ").");

        if (r.getEstado() == EstadoReserva.CANCELADA) {
            log.warn("Pago validado para la reserva cancelada {}: corresponde devolverlo", r.getCodigoReserva());
            return;
        }
        boolean confirma = !"SALDO".equals(tipo) && r.getEstado() == EstadoReserva.PENDIENTE;
        if (confirma) {
            r.setEstado(EstadoReserva.CONFIRMADA);
            r.setConfirmadaEn(ahora);
            operaciones.registrarHito(r, ReservaHistorial.CONFIRMADA, r.saldo().signum() > 0
                    ? "Reserva confirmada. Saldo a pagar en el complejo: " + Dinero.formato(r.saldo()) + "."
                    : "Reserva confirmada y pagada en su totalidad.");
            operaciones.publicar(Eventos.RESERVA_CONFIRMADA,
                    new ReservaConfirmadaEvento(UUID.randomUUID(), ahora, mapeador.datos(r), r.saldo()));
        }
    }

    @Override
    @Transactional
    public void alRechazarPago(PagoRechazadoEvento e) {
        if (!idempotencia.primeraVez(e.idEvento(), Eventos.PAGO_RECHAZADO)) {
            return;
        }
        Optional<Reserva> encontrada = buscar(e.codigoReserva(), Eventos.PAGO_RECHAZADO);
        if (encontrada.isEmpty()) {
            return;
        }
        Reserva r = encontrada.get();
        if (r.getEstado() != EstadoReserva.PENDIENTE) {
            operaciones.registrarHito(r, ReservaHistorial.OBSERVADO, "Comprobante observado: " + e.observacion());
            return;
        }
        LocalDateTime nuevoPlazo = plazoTrasRechazo(r, Tiempo.ahora(), complejo.parametros().minutosParaPagar());
        r.setEstadoPago(EstadoPago.OBSERVADO);
        r.setVenceEn(nuevoPlazo);
        operaciones.registrarHito(r, ReservaHistorial.OBSERVADO, "Comprobante observado: " + e.observacion()
                + ". Reenvíalo antes del " + nuevoPlazo.format(Tiempo.FECHA_HORA) + ".");
    }

    /**
     * {@code min(ahora + 12 h, inicio − 1 h)}; si eso deja menos de {@code minutosParaPagar}, se da ese mínimo
     * (sin pasar del inicio) para que el cliente alcance a reenviar.
     */
    public static LocalDateTime plazoTrasRechazo(Reserva r, LocalDateTime ahora, int minutosParaPagar) {
        LocalDateTime inicio = r.inicio();
        LocalDateTime plazo = min(ahora.plusHours(HORAS_PARA_REENVIAR), inicio.minusHours(1));
        LocalDateTime minimo = min(ahora.plusMinutes(minutosParaPagar), inicio);
        return plazo.isBefore(minimo) ? minimo : plazo;
    }

    private Optional<Reserva> buscar(String codigo, String evento) {
        Optional<Reserva> r = reservas.findByCodigoReserva(OperacionesReserva.normalizarCodigo(codigo));
        if (r.isEmpty()) {
            log.warn("Evento {} para una reserva inexistente: {}", evento, codigo);
        }
        return r;
    }

    private static String texto(String metodo) {
        if (metodo == null) {
            return "";
        }
        return switch (metodo) {
            case "YAPE" -> "Yape";
            case "PLIN" -> "Plin";
            case "TARJETA" -> "tarjeta";
            case "MERCADO_PAGO" -> "Mercado Pago";
            case "EFECTIVO" -> "efectivo";
            case "TARJETA_POS" -> "tarjeta (POS)";
            default -> metodo;
        };
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
