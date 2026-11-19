package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.IntentoPago;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.ProveedorPasarela;
import pe.alcanfores.pagos.evento.BusEventos;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.IntentoPagoRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lado COMERCIO de un pago en línea aprobado o rechazado por el proveedor (webhook o consulta). Idempotente: bloquea
 * la fila del intento y, si ya está APROBADO, no vuelve a cobrar ni emitir comprobante.
 */
@Component
public class AprobacionEnLinea {

    private static final Logger log = LoggerFactory.getLogger(AprobacionEnLinea.class);

    private final IntentoPagoRepositorio intentos;
    private final PagoRepositorio pagos;
    private final DatosFacturacionRepositorio facturacion;
    private final ComprobanteServicio comprobantes;
    private final BusEventos bus;

    public AprobacionEnLinea(IntentoPagoRepositorio intentos, PagoRepositorio pagos, DatosFacturacionRepositorio facturacion,
                             ComprobanteServicio comprobantes, BusEventos bus) {
        this.intentos = intentos;
        this.pagos = pagos;
        this.facturacion = facturacion;
        this.comprobantes = comprobantes;
        this.bus = bus;
    }

    /** Datos que informa el proveedor al aprobar. */
    public record Aprobacion(MetodoPago metodo, String marca, String ultimos4, String codigoAutorizacion,
                             String idExterno) {
    }

    /** @return {@code true} si se registró el pago ahora; {@code false} si ya estaba aprobado (aviso repetido). */
    @Transactional
    public boolean aprobar(String referencia, Aprobacion a) {
        IntentoPago intento = intentos.bloquear(referencia)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos el pago en línea " + referencia + "."));
        if (intento.getEstado() == EstadoIntento.APROBADO) {
            return false;
        }
        // Aunque el intento haya expirado, si el proveedor cobró el dinero se registra el pago (no se pierde un cobro).
        LocalDateTime ahora = Tiempo.ahora();
        String validador = intento.getProveedor() == ProveedorPasarela.BETA ? "Pasarela" : "Mercado Pago";
        Pago p = Pago.nuevo(intento.getCodigoReserva(), intento.getReserva(), a.metodo(), CanalPago.PASARELA,
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
        return true;
    }

    /** Rechazo informado por el proveedor (el cliente puede reintentar mientras el intento no expire). */
    @Transactional
    public void rechazar(String referencia, String motivo) {
        IntentoPago intento = intentos.bloquear(referencia)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado("No encontramos el pago en línea " + referencia + "."));
        if (intento.getEstado() == EstadoIntento.APROBADO || intento.getEstado() == EstadoIntento.EXPIRADO) {
            return;
        }
        intento.setEstado(EstadoIntento.RECHAZADO);
        intento.setMotivoRechazo(motivo == null ? null : motivo.substring(0, Math.min(160, motivo.length())));
        intento.setActualizadoEn(Tiempo.ahora());
    }
}
