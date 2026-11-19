package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.ReservaReprogramadaEvento;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.EventoProcesado;
import pe.alcanfores.pagos.modelo.IntentoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.pagos.repositorio.IntentoPagoRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ConceptoReserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReprogramacionReservaServicioImpl implements ReprogramacionReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReprogramacionReservaServicioImpl.class);

    private final EventoProcesadoRepositorio procesados;
    private final IntentoPagoRepositorio intentos;
    private final PagoRepositorio pagos;

    public ReprogramacionReservaServicioImpl(EventoProcesadoRepositorio procesados, IntentoPagoRepositorio intentos,
                                             PagoRepositorio pagos) {
        this.procesados = procesados;
        this.intentos = intentos;
        this.pagos = pagos;
    }

    @Override
    @Transactional
    public boolean procesar(ReservaReprogramadaEvento evento) {
        String id = evento.idEvento().toString();
        if (procesados.existsById(id)) {
            return false;
        }
        procesados.saveAndFlush(new EventoProcesado(id, Eventos.RESERVA_REPROGRAMADA, Tiempo.ahora()));

        DatosReserva d = evento.reserva();
        String codigo = d.codigoReserva();
        LocalDateTime ahora = Tiempo.ahora();
        String concepto = ConceptoReserva.concepto(d);

        // Con bloqueo: si un webhook está aprobando un intento o el panel validando un pago, se espera y se mira el
        // estado ya confirmado (intentos primero y luego pagos, el mismo orden en cada evento).
        List<IntentoPago> abiertos = intentos.bloquearDeReserva(codigo).stream()
                .filter(i -> i.getEstado().abierto()).toList();
        List<Pago> deLaReserva = pagos.bloquearDeReserva(codigo);
        BigDecimal validado = deLaReserva.stream().filter(p -> p.getEstado() == EstadoValidacion.VALIDADO)
                .map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        int expirados = 0;
        for (IntentoPago i : abiertos) {
            BigDecimal esperado = AprobacionEnLinea.montoDeIntentoNuevo(i.getTipoPago(), d.montoTotal(),
                    d.montoAdelanto(), validado);
            boolean montoAlDia = esperado != null && i.getMonto().compareTo(esperado) == 0;
            if (!anteriorAlCambio(i, evento) || (montoAlDia && concepto.equals(i.getReserva().getConcepto()))) {
                // Creado después del cambio (el evento llegó tarde: reintentos, cola acumulada o DLQ) o ya es
                // exactamente el que se crearía con este horario: se deja abierto.
                continue;
            }
            i.setEstado(EstadoIntento.EXPIRADO);
            // «El monto cambió…» solo si de verdad cambió; con el mismo precio, el motivo neutro (también es un
            // cierre por cambio de la reserva: el checkout lo muestra en lugar de «venció el tiempo»).
            i.setMotivoRechazo(esperado == null || montoAlDia ? AprobacionEnLinea.MOTIVO_CAMBIO_HORARIO
                    : AprobacionEnLinea.MOTIVO_HORARIO);
            i.setActualizadoEn(ahora);
            expirados++;
        }

        Integer porcentaje = ConceptoReserva.porcentaje(d.montoAdelanto(), d.montoTotal());
        List<Pago> enRevision = deLaReserva.stream().filter(p -> p.getEstado() == EstadoValidacion.PENDIENTE).toList();
        enRevision.forEach(p -> p.setReserva(p.getReserva().conHorario(concepto, porcentaje)));

        log.info("Reserva {} reprogramada ({}): {} intento(s) en línea expirado(s) de {} abierto(s), {} pago(s) en "
                        + "revisión con el horario nuevo «{}»", codigo, evento.actor(), expirados, abiertos.size(),
                enRevision.size(), concepto);
        return true;
    }

    /**
     * {@code true} si el intento se creó ANTES del cambio (o en el mismo segundo: los dos relojes guardan segundos), es
     * decir, con los datos del horario anterior. Sin {@code ocurridoEn} se asume que sí. Un intento creado en el mismo
     * segundo pero ya con los datos nuevos se salva por la otra condición (monto y concepto al día).
     */
    private static boolean anteriorAlCambio(IntentoPago i, ReservaReprogramadaEvento evento) {
        return evento.ocurridoEn() == null || i.getCreadoEn() == null || !i.getCreadoEn().isAfter(evento.ocurridoEn());
    }
}
