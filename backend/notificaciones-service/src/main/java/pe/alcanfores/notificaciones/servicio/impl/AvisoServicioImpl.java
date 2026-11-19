package pe.alcanfores.notificaciones.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.ClienteContacto;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.eventos.EventoDominio;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.ReservaCanceladaEvento;
import pe.alcanfores.comun.eventos.ReservaConfirmadaEvento;
import pe.alcanfores.comun.eventos.ReservaRegistradaEvento;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.ReservaInterna;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.EventoProcesado;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.ModeloMensajes;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.AvisoServicio;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Flujo de cada evento:
 * <ol>
 *   <li>si su {@code idEvento} ya está en {@code evento_procesado}, se ignora (idempotencia);</li>
 *   <li>se arman los avisos fuera de la transacción (puede consultar a reservas o catálogo);</li>
 *   <li>en UNA transacción: se marca el evento como procesado, se guardan los avisos (PENDIENTE) y se programa o
 *       cancela el recordatorio;</li>
 *   <li>tras el commit se envían (cada envío deja ENVIADO, SIMULADO o FALLIDO).</li>
 * </ol>
 * Si falta un dato imprescindible (p. ej. reservas no responde y no se sabe a quién avisar) se lanza la excepción:
 * RabbitMQ reintenta 3 veces y luego el mensaje va a la cola de muertos.
 */
@Service
public class AvisoServicioImpl implements AvisoServicio {

    private static final Logger log = LoggerFactory.getLogger(AvisoServicioImpl.class);
    static final String ASISTIO = "ASISTIO";
    static final String CANAL_COMPROBANTE = "COMPROBANTE";

    private final EventoProcesadoRepositorio eventos;
    private final NotificacionRepositorio notificaciones;
    private final RecordatorioServicio recordatorios;
    private final EnvioServicio envio;
    private final FabricaAvisos fabrica;
    private final ModeloMensajes modelos;
    private final ComplejoServicio complejo;
    private final ReservasCliente reservas;
    private final TransactionTemplate tx;

    public AvisoServicioImpl(EventoProcesadoRepositorio eventos, NotificacionRepositorio notificaciones,
                             RecordatorioServicio recordatorios, EnvioServicio envio, FabricaAvisos fabrica,
                             ModeloMensajes modelos, ComplejoServicio complejo, ReservasCliente reservas,
                             TransactionTemplate tx) {
        this.eventos = eventos;
        this.notificaciones = notificaciones;
        this.recordatorios = recordatorios;
        this.envio = envio;
        this.fabrica = fabrica;
        this.modelos = modelos;
        this.complejo = complejo;
        this.reservas = reservas;
        this.tx = tx;
    }

    @Override
    public void alRegistrarReserva(ReservaRegistradaEvento e) {
        procesar(e, Eventos.RESERVA_REGISTRADA, () -> {
            DatosReserva r = e.reserva();
            Map<String, Object> m = modelos.reserva(r, complejo.datos());
            boolean conPlazo = e.venceEn() != null;
            m.put("conPlazo", conPlazo);
            m.put("plazo", conPlazo ? ModeloMensajes.plazoTexto(e.venceEn()) : "");
            String asunto = "Reserva " + r.codigoReserva() + " registrada" + (conPlazo ? ": completa tu pago" : "");
            return fabrica.crear(TipoAviso.RESERVA_REGISTRADA, asunto, m, r.codigoReserva(), r.cliente(),
                    List.of(Adjunto.constancia(r.codigoReserva())));
        }, null);
    }

    @Override
    public void alRegistrarPago(PagoRegistradoEvento e) {
        procesar(e, Eventos.PAGO_REGISTRADO, () -> {
            if (e.canal() != null && !CANAL_COMPROBANTE.equals(e.canal())) {
                // Pasarela y caja se validan al instante: el cliente recibe directamente la confirmación y su comprobante.
                log.info("pago.registrado {} por {}: no requiere aviso de «comprobante recibido»", e.codigoReserva(), e.canal());
                return List.of();
            }
            ReservaInterna ri = reservas.consultar(e.codigoReserva());
            Map<String, Object> m = modelos.reserva(ri.datos(), complejo.datos());
            m.put("metodo", metodoTexto(e.metodo()));
            m.put("montoPago", Dinero.formato(e.monto() == null ? BigDecimal.ZERO : e.monto()));
            m.put("tipoPago", tipoPagoTexto(e.tipoPago()));
            String asunto = "Recibimos tu comprobante de pago · " + e.codigoReserva();
            return fabrica.crear(TipoAviso.PAGO_REGISTRADO, asunto, m, e.codigoReserva(), ri.datos().cliente(), List.of());
        }, null);
    }

    @Override
    public void alRechazarPago(PagoRechazadoEvento e) {
        procesar(e, Eventos.PAGO_RECHAZADO, () -> {
            ReservaInterna ri = reservas.consultar(e.codigoReserva());
            Map<String, Object> m = modelos.reserva(ri.datos(), complejo.datos());
            m.put("observacion", e.observacion() == null ? "" : e.observacion().trim());
            boolean conPlazo = ri.venceEn() != null;
            m.put("conPlazo", conPlazo);
            m.put("plazo", conPlazo ? ModeloMensajes.plazoTexto(ri.venceEn()) : "");
            String asunto = "Revisa tu pago de la reserva " + e.codigoReserva();
            return fabrica.crear(TipoAviso.PAGO_RECHAZADO, asunto, m, e.codigoReserva(), ri.datos().cliente(), List.of());
        }, null);
    }

    @Override
    public void alConfirmarReserva(ReservaConfirmadaEvento e) {
        DatosReserva r = e.reserva();
        int[] horas = new int[1];
        procesar(e, Eventos.RESERVA_CONFIRMADA, () -> {
            DatosComplejo c = complejo.datos();
            horas[0] = c.recordatorioHoras();
            Map<String, Object> m = modelos.reserva(r, c);
            modelos.fijarSaldo(m, saldo(e.saldoPendiente(), r));
            String asunto = "Reserva confirmada · " + r.codigoReserva() + " · " + m.get("fechaCorta") + " "
                    + m.get("horaInicio");
            return fabrica.crear(TipoAviso.RESERVA_CONFIRMADA, asunto, m, r.codigoReserva(), r.cliente(),
                    List.of(Adjunto.constancia(r.codigoReserva())));
        }, () -> recordatorios.programar(r, saldo(e.saldoPendiente(), r), horas[0]));
    }

    @Override
    public void alEmitirComprobante(ComprobanteEmitidoEvento e) {
        procesar(e, Eventos.COMPROBANTE_EMITIDO, () -> {
            Map<String, Object> m = modelos.base(complejo.datos());
            ClienteContacto cli = e.cliente();
            String nombre = cli == null || cli.nombres() == null || cli.nombres().isBlank() ? null
                    : cli.nombres().trim().split("\\s+")[0];
            m.put("saludo", nombre == null ? "Hola" : "Hola, " + nombre);
            m.put("nombreCompleto", cli == null ? "" : cli.nombreCompleto());
            String tipo = tipoComprobanteTexto(e.tipo());
            boolean notaCredito = "NOTA_CREDITO".equals(e.tipo());
            m.put("tipoComprobante", tipo);
            m.put("notaCredito", notaCredito);
            m.put("serieNumero", e.serieNumero());
            m.put("total", Dinero.formato(e.total() == null ? BigDecimal.ZERO : e.total()));
            m.put("fechaEmision", ModeloMensajes.fechaHoraTexto(e.ocurridoEn() == null ? Tiempo.ahora() : e.ocurridoEn()));
            boolean conReserva = e.codigoReserva() != null && !e.codigoReserva().isBlank();
            m.put("conReserva", conReserva);
            m.put("codigo", conReserva ? e.codigoReserva() : "");
            m.put("urlMiReserva", conReserva ? modelos.urlMiReserva(e.codigoReserva()) : modelos.frontUrl());
            String asunto = (notaCredito ? "Nota de crédito " : "Tu " + tipo + " ") + e.serieNumero()
                    + (conReserva ? " · " + e.codigoReserva() : "");
            return fabrica.crear(TipoAviso.COMPROBANTE_EMITIDO, asunto, m, conReserva ? e.codigoReserva() : null, cli,
                    List.of(Adjunto.comprobante(e.idComprobante(), e.serieNumero())));
        }, null);
    }

    @Override
    public void alCancelarReserva(ReservaCanceladaEvento e) {
        DatosReserva r = e.reserva();
        procesar(e, Eventos.RESERVA_CANCELADA, () -> {
            Map<String, Object> m = modelos.reserva(r, complejo.datos());
            boolean porSistema = "SISTEMA".equals(e.canceladoPor());
            BigDecimal pagado = r.montoPagado() == null ? BigDecimal.ZERO : r.montoPagado();
            m.put("motivo", e.motivo() == null ? "" : e.motivo().trim());
            m.put("canceladoPor", canceladoPorTexto(e.canceladoPor()));
            m.put("porSistema", porSistema);
            m.put("devolucion", e.devolucion());
            m.put("montoDevolucion", Dinero.formato(e.montoDevolucion() == null ? pagado : e.montoDevolucion()));
            m.put("sinDevolucion", !e.devolucion() && pagado.signum() > 0);
            String asunto = "Reserva " + r.codigoReserva() + " cancelada";
            return fabrica.crear(TipoAviso.RESERVA_CANCELADA, asunto, m, r.codigoReserva(), r.cliente(), List.of());
        }, () -> recordatorios.cancelar(r.codigoReserva(), "Reserva cancelada (" + e.canceladoPor() + ")."));
    }

    @Override
    public void alRegistrarAsistencia(AsistenciaRegistradaEvento e) {
        procesar(e, Eventos.RESERVA_ASISTENCIA, () -> {
            if (!ASISTIO.equals(e.asistencia())) {
                return List.of();
            }
            DatosReserva r = e.reserva();
            Map<String, Object> m = modelos.reserva(r, complejo.datos());
            String asunto = "¿Qué tal tu partido? Califica " + r.nombreCancha();
            return fabrica.crear(TipoAviso.INVITACION_RESENA, asunto, m, r.codigoReserva(), r.cliente(), List.of());
        }, null);
    }

    private void procesar(EventoDominio evento, String tipo, Supplier<List<Notificacion>> armar, Runnable enTransaccion) {
        String id = evento.idEvento().toString();
        if (eventos.existsById(id)) {
            log.info("Evento {} {} ya procesado: se ignora", tipo, id);
            return;
        }
        List<Notificacion> nuevas = armar.get();
        List<Notificacion> guardadas;
        try {
            guardadas = tx.execute(estado -> {
                eventos.saveAndFlush(new EventoProcesado(id, tipo, Tiempo.ahora()));
                if (enTransaccion != null) {
                    enTransaccion.run();
                }
                return notificaciones.saveAll(nuevas);
            });
        } catch (DataIntegrityViolationException duplicado) {
            log.info("Evento {} {} llegó duplicado al mismo tiempo: lo procesó otra copia", tipo, id);
            return;
        }
        if (guardadas != null && !guardadas.isEmpty()) {
            envio.enviarTodas(guardadas);
        }
    }

    private static BigDecimal saldo(BigDecimal saldoPendiente, DatosReserva r) {
        if (saldoPendiente != null) {
            return saldoPendiente;
        }
        BigDecimal total = r.montoTotal() == null ? BigDecimal.ZERO : r.montoTotal();
        BigDecimal pagado = r.montoPagado() == null ? BigDecimal.ZERO : r.montoPagado();
        return total.subtract(pagado).max(BigDecimal.ZERO);
    }

    static String metodoTexto(String metodo) {
        if (metodo == null) {
            return "";
        }
        return switch (metodo) {
            case "YAPE" -> "Yape";
            case "PLIN" -> "Plin";
            case "TARJETA" -> "Tarjeta";
            case "MERCADO_PAGO" -> "Mercado Pago";
            case "EFECTIVO" -> "Efectivo";
            case "TARJETA_POS" -> "Tarjeta (POS)";
            default -> metodo;
        };
    }

    static String tipoPagoTexto(String tipoPago) {
        if (tipoPago == null) {
            return "pago";
        }
        return switch (tipoPago) {
            case "ADELANTO" -> "adelanto";
            case "TOTAL" -> "pago total";
            case "SALDO" -> "saldo";
            default -> tipoPago.toLowerCase();
        };
    }

    static String tipoComprobanteTexto(String tipo) {
        if (tipo == null) {
            return "comprobante electrónico";
        }
        return switch (tipo) {
            case "BOLETA" -> "boleta electrónica";
            case "FACTURA" -> "factura electrónica";
            case "NOTA_CREDITO" -> "nota de crédito electrónica";
            default -> "comprobante electrónico";
        };
    }

    static String canceladoPorTexto(String actor) {
        if (actor == null) {
            return "el complejo";
        }
        return switch (actor) {
            case "CLIENTE" -> "ti";
            case "ADMINISTRADOR" -> "el complejo";
            case "SISTEMA" -> "el sistema";
            default -> actor.toLowerCase();
        };
    }
}
