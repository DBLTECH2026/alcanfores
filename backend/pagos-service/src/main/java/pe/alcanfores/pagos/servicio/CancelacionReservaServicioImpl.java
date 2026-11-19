package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.evento.BusEventos;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.EstadoComprobante;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.EventoProcesado;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.repositorio.ComprobanteRepositorio;
import pe.alcanfores.pagos.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.pagos.repositorio.IntentoPagoRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class CancelacionReservaServicioImpl implements CancelacionReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(CancelacionReservaServicioImpl.class);

    private final EventoProcesadoRepositorio procesados;
    private final PagoRepositorio pagos;
    private final IntentoPagoRepositorio intentos;
    private final ComprobanteRepositorio comprobantes;
    private final ComprobanteServicio comprobanteServicio;
    private final BusEventos bus;

    public CancelacionReservaServicioImpl(EventoProcesadoRepositorio procesados, PagoRepositorio pagos,
                                          IntentoPagoRepositorio intentos, ComprobanteRepositorio comprobantes,
                                          ComprobanteServicio comprobanteServicio, BusEventos bus) {
        this.procesados = procesados;
        this.pagos = pagos;
        this.intentos = intentos;
        this.comprobantes = comprobantes;
        this.comprobanteServicio = comprobanteServicio;
        this.bus = bus;
    }

    @Override
    @Transactional
    public boolean procesar(ReservaCanceladaEvento evento) {
        String id = evento.idEvento().toString();
        if (procesados.existsById(id)) {
            return false;
        }
        procesados.saveAndFlush(new EventoProcesado(id, Eventos.RESERVA_CANCELADA, Tiempo.ahora()));

        String codigo = evento.reserva().codigoReserva();
        LocalDateTime ahora = Tiempo.ahora();
        String motivo = Texto.limpiar(evento.motivo()) == null ? "Sin motivo" : Texto.limpiar(evento.motivo());

        List<Pago> pendientes = pagos.findByCodigoReservaAndEstadoIn(codigo, EnumSet.of(EstadoValidacion.PENDIENTE));
        pendientes.forEach(p -> {
            p.setEstado(EstadoValidacion.ANULADO);
            p.setObservacion(recortar("Reserva cancelada: " + motivo));
            p.setFechaValidacion(ahora);
            p.setValidadoPor("Sistema");
        });

        intentos.findByCodigoReservaAndEstadoIn(codigo,
                EnumSet.of(EstadoIntento.CREADO, EstadoIntento.REQUIERE_3DS, EstadoIntento.RECHAZADO)).forEach(i -> {
            i.setEstado(EstadoIntento.EXPIRADO);
            i.setMotivoRechazo("La reserva fue cancelada.");
            i.setActualizadoEn(ahora);
        });

        int notas = 0;
        BigDecimal reembolsado = BigDecimal.ZERO;
        if (evento.devolucion()) {
            List<Pago> cobrados = pagos.findByCodigoReservaAndEstadoIn(codigo, EnumSet.of(EstadoValidacion.VALIDADO));
            for (Pago p : cobrados) {
                p.setEstado(EstadoValidacion.REEMBOLSADO);
                p.setObservacion(recortar("Reembolso simulado por cancelación: " + motivo));
                reembolsado = reembolsado.add(p.getMonto());
                for (Comprobante c : comprobantes.findByIdPagoOrderByIdAsc(p.getId())) {
                    if (c.getTipo() != TipoComprobante.NOTA_CREDITO && c.getEstado() == EstadoComprobante.EMITIDO) {
                        comprobanteServicio.emitirNotaCredito(c, ComprobanteServicio.MOTIVO_DEVOLUCION,
                                recortar("Devolución por cancelación de la reserva " + codigo + ": " + motivo), ahora, true);
                        notas++;
                    }
                }
            }
        }
        if (reembolsado.signum() > 0) {
            bus.auditarComo("Sistema", "SISTEMA", "REEMBOLSO_SIMULADO", "reserva", codigo,
                    "Reembolso de " + Dinero.formato(reembolsado) + " con " + notas + " nota(s) de crédito · " + motivo);
        }
        log.info("Reserva {} cancelada: {} pago(s) anulado(s), reembolso {}, {} nota(s) de crédito", codigo,
                pendientes.size(), Dinero.formato(reembolsado), notas);
        return true;
    }

    private static String recortar(String texto) {
        return texto.length() > 255 ? texto.substring(0, 252) + "..." : texto;
    }
}
