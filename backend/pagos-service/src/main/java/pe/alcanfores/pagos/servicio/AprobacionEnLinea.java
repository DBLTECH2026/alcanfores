package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.cliente.ReservasCliente;
import pe.alcanfores.pagos.evento.BusEventos;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.IntentoPago;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.ProveedorPasarela;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.IntentoPagoRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ConceptoReserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado COMERCIO de un pago en línea aprobado o rechazado por el proveedor (webhook o consulta). Idempotente: bloquea
 * la fila del intento y, si ya está APROBADO, no vuelve a cobrar ni emitir comprobante.
 * <p>Antes de aprobar vuelve a mirar la reserva: si cambió de horario (o entró otro cobro) y el monto del intento ya
 * supera el saldo vigente, NO registra el pago; deja el intento EXPIRADO con un motivo que la página de resultado ya
 * sabe mostrar. La reserva se lee por HTTP con {@link #reservaVigente(String)} ANTES de abrir la transacción. Esa
 * decisión queda fija para esa operación del proveedor (la bitácora ya pidió devolverla).</p>
 */
@Component
public class AprobacionEnLinea {

    private static final Logger log = LoggerFactory.getLogger(AprobacionEnLinea.class);

    /** La reserva cambió de horario y el intento quedó con el monto viejo (lo lee el cliente). */
    public static final String MOTIVO_HORARIO =
            "El monto cambió porque la reserva cambió de horario; vuelve a iniciar el pago.";
    /** La reserva cambió de horario pero el monto sigue siendo el mismo (otra cancha u hora del mismo precio). */
    public static final String MOTIVO_CAMBIO_HORARIO = "Tu reserva cambió de horario; vuelve a iniciar el pago.";
    /** El saldo bajó sin cambio de horario (otro cobro entró mientras el intento seguía abierto). */
    public static final String MOTIVO_SALDO = "El saldo de tu reserva cambió; vuelve a iniciar el pago.";
    /** Se agrega cuando el proveedor YA cobró: ese dinero no se registra y el complejo debe devolverlo. */
    static final String AVISO_DEVOLUCION = " Te devolveremos lo cobrado.";
    static final String ACCION_NO_APLICADO = "PAGO_EN_LINEA_NO_APLICADO";

    private final IntentoPagoRepositorio intentos;
    private final PagoRepositorio pagos;
    private final DatosFacturacionRepositorio facturacion;
    private final ComprobanteServicio comprobantes;
    private final ReservasCliente reservas;
    private final BusEventos bus;
    private final TransactionTemplate tx;

    public AprobacionEnLinea(IntentoPagoRepositorio intentos, PagoRepositorio pagos, DatosFacturacionRepositorio facturacion,
                             ComprobanteServicio comprobantes, ReservasCliente reservas, BusEventos bus,
                             PlatformTransactionManager transacciones) {
        this.intentos = intentos;
        this.pagos = pagos;
        this.facturacion = facturacion;
        this.comprobantes = comprobantes;
        this.reservas = reservas;
        this.bus = bus;
        this.tx = new TransactionTemplate(transacciones);
    }

    /** Datos que informa el proveedor al aprobar. */
    public record Aprobacion(MetodoPago metodo, String marca, String ultimos4, String codigoAutorizacion,
                             String idExterno) {
    }

    /** Qué pasó con un aviso de pago aprobado. */
    public enum Resultado {
        /** Se registró el pago ahora (VALIDADO + comprobante + {@code pago.validado}). */
        REGISTRADO,
        /** Aviso repetido: el intento ya estaba aprobado. */
        YA_APROBADO,
        /**
         * El monto del intento supera el saldo vigente: no se registra y el intento queda EXPIRADO (devolver). También
         * cuando esa misma operación ya se había dejado sin aplicar (la decisión no cambia).
         */
        NO_APLICADO
    }

    /** Monto que ya no cabe en la reserva: saldo vigente y motivo para el cliente. */
    private record MontoDesactualizado(BigDecimal saldo, String motivo) {
    }

    /**
     * Reserva vigente del intento (HTTP a reservas-service; llamar FUERA de una transacción). Vacía si el intento no
     * existe, ya está aprobado o reservas no conoce el código.
     *
     * @throws NegocioExcepcion 503 si reservas-service no responde (el webhook falla y el proveedor reintenta).
     */
    public Optional<ReservaInterna> reservaVigente(String referencia) {
        if (referencia == null) {
            return Optional.empty();
        }
        return intentos.findByReferencia(referencia)
                .filter(i -> i.getEstado() != EstadoIntento.APROBADO)
                .flatMap(i -> reservas.buscar(i.getCodigoReserva()));
    }

    /** Para quien no tiene una transacción abierta (sondeo «Ya pagué, verificar»): lee la reserva y aprueba. */
    public Resultado aprobar(String referencia, Aprobacion a) {
        return aprobar(referencia, a, reservaVigente(referencia).orElse(null));
    }

    /**
     * @param vigente la reserva leída con {@link #reservaVigente(String)} antes de abrir la transacción; {@code null}
     *                si reservas no la conoce: entonces se aprueba con la fotografía del intento (no se pierde un cobro).
     */
    public Resultado aprobar(String referencia, Aprobacion a, ReservaInterna vigente) {
        return tx.execute(estado -> aprobarBloqueado(referencia, a, vigente));
    }

    private Resultado aprobarBloqueado(String referencia, Aprobacion a, ReservaInterna vigente) {
        IntentoPago intento = bloquear(referencia);
        if (intento.getEstado() == EstadoIntento.APROBADO) {
            return Resultado.YA_APROBADO;
        }
        if (yaNoAplicado(intento, a)) {
            // La bitácora ya pidió devolver ESTA operación: aunque ahora el saldo alcance (otro cambio a un horario más
            // caro, un cobro de caja anulado), no se registra; si no, se devolvería un cobro que la reserva cuenta.
            return Resultado.NO_APLICADO;
        }
        LocalDateTime ahora = Tiempo.ahora();
        String validador = intento.getProveedor() == ProveedorPasarela.BETA ? "Pasarela" : "Mercado Pago";
        MontoDesactualizado desactualizado = montoDesactualizado(intento, vigente);
        if (desactualizado != null) {
            return noAplicar(intento, a, desactualizado, validador, ahora);
        }
        // Aunque el intento haya expirado, si el proveedor cobró el dinero se registra el pago (no se pierde un cobro).
        // La fotografía sale de la reserva vigente: si se reprogramó a un horario más caro, la boleta lleva el nuevo.
        InstantaneaReserva foto = vigente == null ? intento.getReserva()
                : intento.getReserva().conHorario(VerificadorReservas.concepto(vigente),
                ConceptoReserva.porcentaje(vigente.montoAdelanto(), vigente.montoTotal()));
        Pago p = Pago.nuevo(intento.getCodigoReserva(), foto, a.metodo(), CanalPago.PASARELA,
                intento.getTipoPago(), intento.getMonto(), EstadoValidacion.PENDIENTE, intento.getCreadoEn());
        p.setReferenciaPasarela(referencia);
        p.setMarcaTarjeta(a.marca());
        p.setUltimos4(a.ultimos4());
        p.setCodigoAutorizacion(a.codigoAutorizacion());
        p.setCodigoOperacion(a.idExterno());
        p.validar(ahora, null, validador);
        pagos.save(p);
        facturacion.findFirstByReferenciaIntentoOrderByIdDesc(referencia).ifPresent(df -> df.setIdPago(p.getId()));
        intento.setEstado(EstadoIntento.APROBADO);
        intento.setMotivoRechazo(null);
        intento.setIdPago(p.getId());
        intento.setIdExterno(a.idExterno());
        intento.setActualizadoEn(ahora);
        // flush para que el comprobante encuentre los datos de facturación ya enlazados al pago
        pagos.flush();
        comprobantes.emitirParaPago(p, ahora, true);
        bus.publicar(Eventos.PAGO_VALIDADO, new PagoValidadoEvento(UUID.randomUUID(), ahora, p.getId(),
                p.getCodigoReserva(), p.getMetodo().name(), CanalPago.PASARELA.name(), p.getMonto(),
                p.getTipoPago().name(), validador));
        log.info("Pago en línea aprobado {} · reserva {} · {}", referencia, p.getCodigoReserva(), validador);
        return Resultado.REGISTRADO;
    }

    /**
     * El proveedor ya cobró un monto que la reserva ya no debe: no se registra el pago (ni comprobante ni
     * {@code pago.validado}); el intento queda EXPIRADO con el motivo + aviso de devolución y la bitácora avisa al
     * complejo para devolverlo. Solo llega aquí una operación nueva: un aviso repetido de la misma operación sale antes
     * con {@link #yaNoAplicado(IntentoPago, Aprobacion)} (no vuelve a la bitácora).
     */
    private Resultado noAplicar(IntentoPago intento, Aprobacion a, MontoDesactualizado d, String validador,
                                LocalDateTime ahora) {
        intento.setEstado(EstadoIntento.EXPIRADO);
        intento.setMotivoRechazo(d.motivo() + AVISO_DEVOLUCION);
        if (a.idExterno() != null) {
            intento.setIdExterno(a.idExterno());
        }
        intento.setActualizadoEn(ahora);
        String operacion = a.idExterno() == null ? "" : ", operación " + a.idExterno();
        bus.auditarComo("Sistema", "SISTEMA", ACCION_NO_APLICADO, "reserva", intento.getCodigoReserva(),
                recortar(validador + " cobró " + Dinero.formato(intento.getMonto()) + " (" + intento.getReferencia()
                        + operacion + ") pero el saldo vigente es " + Dinero.formato(d.saldo())
                        + ": no se registró; corresponde devolverlo al cliente."));
        log.warn("Pago en línea {} NO aplicado · reserva {} · cobrado {} > saldo vigente {} · devolver",
                intento.getReferencia(), intento.getCodigoReserva(), Dinero.formato(intento.getMonto()),
                Dinero.formato(d.saldo()));
        return Resultado.NO_APLICADO;
    }

    /**
     * {@code true} si esta MISMA operación del proveedor ya se dejó sin aplicar (intento EXPIRADO con el aviso de
     * devolución y el mismo id externo): la decisión es definitiva por operación.
     */
    private static boolean yaNoAplicado(IntentoPago intento, Aprobacion a) {
        return intento.getEstado() == EstadoIntento.EXPIRADO && intento.getMotivoRechazo() != null
                && intento.getMotivoRechazo().endsWith(AVISO_DEVOLUCION)
                && Objects.equals(a.idExterno(), intento.getIdExterno());
    }

    /**
     * Antes de que la pasarela beta cobre: lee la reserva (fuera de transacción) y cierra el intento (EXPIRADO, sin
     * aviso de devolución porque aún no se cobró nada) si ya no es el que se crearía ahora. La regla es más estricta
     * que al aprobar (cerrar aquí no pierde dinero): el monto debe ser el de un intento nuevo
     * ({@link #montoDeIntentoNuevo}) y el concepto el del horario vigente. Así no se cobra un adelanto viejo menor al
     * vigente ni un «Pago total» viejo que ya no cubre el total.
     *
     * @return el motivo para mostrar al cliente, o vacío si se puede cobrar.
     */
    public Optional<String> cerrarSiCambioElMonto(String referencia) {
        ReservaInterna vigente = reservaVigente(referencia).orElse(null);
        if (vigente == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tx.execute(estado -> {
            IntentoPago intento = bloquear(referencia);
            if (!intento.getEstado().abierto()) {
                return null;
            }
            String motivo = motivoParaNoCobrar(intento, vigente);
            if (motivo == null) {
                return null;
            }
            intento.setEstado(EstadoIntento.EXPIRADO);
            intento.setMotivoRechazo(motivo);
            intento.setActualizadoEn(Tiempo.ahora());
            log.info("Intento {} cerrado antes de cobrar · reserva {} · monto {} · {}", referencia,
                    intento.getCodigoReserva(), Dinero.formato(intento.getMonto()), motivo);
            return motivo;
        }));
    }

    /**
     * {@code true} si el intento se cerró porque la reserva cambió (de horario o de saldo), no por tiempo: el checkout y
     * el sondeo muestran ese motivo en lugar de «venció el tiempo».
     */
    public static boolean cerradoPorMonto(String motivoRechazo) {
        return motivoRechazo != null && (motivoRechazo.startsWith(MOTIVO_HORARIO)
                || motivoRechazo.startsWith(MOTIVO_CAMBIO_HORARIO) || motivoRechazo.startsWith(MOTIVO_SALDO));
    }

    /**
     * Monto que tendría un intento NUEVO de ese tipo con la reserva dada (la misma regla que al crearlo): ADELANTO → el
     * adelanto; TOTAL → total − lo VALIDADO según pagos. {@code null} si falta el dato.
     */
    public static BigDecimal montoDeIntentoNuevo(TipoPago tipo, BigDecimal total, BigDecimal adelanto,
                                                 BigDecimal validado) {
        if (tipo == TipoPago.ADELANTO) {
            return adelanto;
        }
        return total == null ? null : total.subtract(validado == null ? BigDecimal.ZERO : validado);
    }

    /** Motivo para cerrar un intento antes de cobrar, o {@code null} si es el que se crearía ahora. */
    private String motivoParaNoCobrar(IntentoPago intento, ReservaInterna vigente) {
        if (vigente.montoTotal() == null) {
            return null;
        }
        BigDecimal validado = pagos.sumarPorEstado(intento.getCodigoReserva(), EstadoValidacion.VALIDADO);
        BigDecimal saldo = vigente.montoTotal().subtract(validado);
        BigDecimal esperado = montoDeIntentoNuevo(intento.getTipoPago(), vigente.montoTotal(), vigente.montoAdelanto(),
                validado);
        boolean montoViejo = intento.getMonto().compareTo(saldo) > 0
                || (esperado != null && intento.getMonto().compareTo(esperado) != 0);
        boolean otroHorario = !VerificadorReservas.concepto(vigente).equals(intento.getReserva().getConcepto());
        if (montoViejo) {
            return otroHorario ? MOTIVO_HORARIO : MOTIVO_SALDO;
        }
        return otroHorario ? MOTIVO_CAMBIO_HORARIO : null;
    }

    /**
     * Saldo vigente = total que informa reservas − lo VALIDADO según pagos (dueño de los cobros, como la caja). Si el
     * monto del intento lo supera, devuelve el saldo y el motivo; {@code null} si se puede cobrar o no hay con qué
     * comparar.
     */
    private MontoDesactualizado montoDesactualizado(IntentoPago intento, ReservaInterna vigente) {
        if (vigente == null || vigente.montoTotal() == null) {
            return null;
        }
        BigDecimal saldo = vigente.montoTotal()
                .subtract(pagos.sumarPorEstado(intento.getCodigoReserva(), EstadoValidacion.VALIDADO));
        if (intento.getMonto().compareTo(saldo) <= 0) {
            return null;
        }
        boolean otroHorario = !VerificadorReservas.concepto(vigente).equals(intento.getReserva().getConcepto());
        return new MontoDesactualizado(saldo, otroHorario ? MOTIVO_HORARIO : MOTIVO_SALDO);
    }

    private IntentoPago bloquear(String referencia) {
        return intentos.bloquear(referencia)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos el pago en línea " + referencia + "."));
    }

    /** Rechazo informado por el proveedor (el cliente puede reintentar mientras el intento no expire). */
    @Transactional
    public void rechazar(String referencia, String motivo) {
        IntentoPago intento = bloquear(referencia);
        if (intento.getEstado() == EstadoIntento.APROBADO || intento.getEstado() == EstadoIntento.EXPIRADO) {
            return;
        }
        intento.setEstado(EstadoIntento.RECHAZADO);
        intento.setMotivoRechazo(motivo == null ? null : motivo.substring(0, Math.min(160, motivo.length())));
        intento.setActualizadoEn(Tiempo.ahora());
    }

    private static String recortar(String texto) {
        return texto.length() > 255 ? texto.substring(0, 252) + "..." : texto;
    }
}
