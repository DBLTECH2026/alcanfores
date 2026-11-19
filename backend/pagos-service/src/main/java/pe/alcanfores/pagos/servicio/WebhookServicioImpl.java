package pe.alcanfores.pagos.servicio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PagoMp;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.config.PagosPropiedades;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.ProveedorPasarela;
import pe.alcanfores.pagos.modelo.WebhookEvento;
import pe.alcanfores.pagos.pasarela.simulador.FirmaHmac;
import pe.alcanfores.pagos.repositorio.WebhookEventoRepositorio;

import java.util.Optional;
import java.util.function.Supplier;

@Service
public class WebhookServicioImpl implements WebhookServicio {

    private static final Logger log = LoggerFactory.getLogger(WebhookServicioImpl.class);
    static final String TIPO_APROBADO = "pago.aprobado";
    static final String TIPO_RECHAZADO = "pago.rechazado";

    private final WebhookEventoRepositorio eventos;
    private final AprobacionEnLinea aprobacion;
    private final MercadoPagoCliente mercadoPago;
    private final PagosPropiedades propiedades;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public WebhookServicioImpl(WebhookEventoRepositorio eventos, AprobacionEnLinea aprobacion,
                               MercadoPagoCliente mercadoPago, PagosPropiedades propiedades, ObjectMapper json,
                               PlatformTransactionManager transacciones) {
        this.eventos = eventos;
        this.aprobacion = aprobacion;
        this.mercadoPago = mercadoPago;
        this.propiedades = propiedades;
        this.json = json;
        this.tx = new TransactionTemplate(transacciones);
    }

    @Override
    public RespuestaWebhook recibirPasarelaBeta(String cuerpo, String firma, String idEvento) {
        if (!FirmaHmac.verificar(propiedades.getPasarela().getSecreto(), cuerpo, firma)) {
            registrarInvalido(ProveedorPasarela.BETA, cuerpo);
            throw new NegocioExcepcion(401, "FIRMA_INVALIDA", "La firma del aviso no es válida.");
        }
        if (idEvento == null || idEvento.isBlank() || idEvento.length() > 80) {
            throw NegocioExcepcion.validacion("X-Evento-Id", "Falta el identificador del evento.");
        }
        JsonNode aviso = leer(cuerpo).orElseThrow(() -> NegocioExcepcion.validacion("cuerpo", "El aviso no es un JSON válido."));
        String tipo = aviso.path("tipo").asText("");
        String referencia = aviso.path("referencia").asText(null);
        // La reserva vigente se lee ANTES de abrir la transacción (HTTP fuera de ella); si reservas no responde, 503
        // sin registrar nada y la pasarela reintenta el aviso.
        ReservaInterna vigente = TIPO_APROBADO.equals(tipo) ? aprobacion.reservaVigente(referencia).orElse(null) : null;
        return procesarUnaVez(ProveedorPasarela.BETA, idEvento.trim(), tipo, cuerpo, () -> {
            if (TIPO_APROBADO.equals(tipo)) {
                MetodoPago metodo = "YAPE".equals(aviso.path("metodo").asText()) ? MetodoPago.YAPE : MetodoPago.TARJETA;
                return texto(aprobacion.aprobar(referencia, new AprobacionEnLinea.Aprobacion(metodo,
                        aviso.path("marca").asText(null), aviso.path("ultimos4").asText(null),
                        aviso.path("codigoAutorizacion").asText(null), aviso.path("idTransaccion").asText(null)), vigente));
            }
            if (TIPO_RECHAZADO.equals(tipo)) {
                aprobacion.rechazar(referencia, aviso.path("motivo").asText(null));
                return "Rechazo registrado";
            }
            return "Tipo de evento ignorado";
        });
    }

    @Override
    public RespuestaWebhook recibirMercadoPago(String cuerpo, String dataIdConsulta, String tipoConsulta, String firma,
                                               String idSolicitud) {
        PagosPropiedades.MercadoPago mp = propiedades.getMercadopago();
        if (!mp.configurado()) {
            return new RespuestaWebhook(true, false, "Mercado Pago no está configurado: aviso ignorado");
        }
        JsonNode aviso = leer(cuerpo).orElse(json.createObjectNode());
        String dataId = dataIdConsulta != null ? dataIdConsulta : aviso.path("data").path("id").asText(null);
        String tipo = tipoConsulta != null ? tipoConsulta : aviso.path("type").asText(aviso.path("topic").asText(""));
        if (mp.getWebhookSecreto() != null && !mp.getWebhookSecreto().isBlank()
                && !FirmasMercadoPago.valida(mp.getWebhookSecreto(), firma, dataId, idSolicitud)) {
            registrarInvalido(ProveedorPasarela.MERCADO_PAGO, cuerpo);
            throw new NegocioExcepcion(401, "FIRMA_INVALIDA", "La firma del aviso no es válida.");
        }
        if (!"payment".equals(tipo) || dataId == null || dataId.isBlank()) {
            return new RespuestaWebhook(true, false, "Tipo de aviso ignorado");
        }
        Optional<PagoMp> consultado = mercadoPago.obtenerPago(dataId);
        if (consultado.isEmpty() || consultado.get().referenciaExterna() == null) {
            return new RespuestaWebhook(true, false, "Pago desconocido: aviso ignorado");
        }
        PagoMp pago = consultado.get();
        String idExterno = "payment:" + pago.id() + ":" + pago.estado();
        ReservaInterna vigente = pago.aprobado() ? aprobacion.reservaVigente(pago.referenciaExterna()).orElse(null) : null;
        return procesarUnaVez(ProveedorPasarela.MERCADO_PAGO, idExterno, "payment." + pago.estado(), cuerpo, () -> {
            if (pago.aprobado()) {
                return texto(aprobacion.aprobar(pago.referenciaExterna(), IntentoPagoServicioImpl.aprobacionDe(pago),
                        vigente));
            }
            if (pago.rechazado()) {
                aprobacion.rechazar(pago.referenciaExterna(), IntentoPagoServicioImpl.motivoMercadoPago(pago.detalleEstado()));
                return "Rechazo registrado";
            }
            return "Estado " + pago.estado() + " sin acción";
        });
    }

    static String texto(AprobacionEnLinea.Resultado resultado) {
        return switch (resultado) {
            case REGISTRADO -> "Pago aprobado y registrado";
            case YA_APROBADO -> "El pago ya estaba aprobado";
            case NO_APLICADO -> "No aplicado: el monto supera el saldo vigente de la reserva (devolver al cliente)";
        };
    }

    /**
     * Inserta el evento (único por proveedor + id externo) y lo procesa en la MISMA transacción: si el proceso falla
     * no queda registrado y el proveedor puede reintentar; si llega repetido (o en paralelo) se responde «duplicado».
     */
    private RespuestaWebhook procesarUnaVez(ProveedorPasarela proveedor, String idExterno, String tipo, String cuerpo,
                                            Supplier<String> proceso) {
        try {
            return tx.execute(estado -> {
                if (eventos.existsByProveedorAndIdExterno(proveedor, idExterno)) {
                    return new RespuestaWebhook(true, true, "Aviso repetido: ya fue procesado");
                }
                WebhookEvento e = nuevo(proveedor, idExterno, tipo, cuerpo, true);
                eventos.saveAndFlush(e);
                e.setResultado(proceso.get());
                e.setProcesado(true);
                return new RespuestaWebhook(true, false, e.getResultado());
            });
        } catch (DataIntegrityViolationException carrera) {
            log.info("Aviso {} {} recibido en paralelo: se procesa una sola vez", proveedor, idExterno);
            return new RespuestaWebhook(true, true, "Aviso repetido: ya fue procesado");
        }
    }

    private void registrarInvalido(ProveedorPasarela proveedor, String cuerpo) {
        log.warn("Aviso de {} con firma inválida rechazado", proveedor);
        tx.executeWithoutResult(estado -> {
            WebhookEvento e = nuevo(proveedor, null, "firma.invalida", cuerpo, false);
            e.setResultado("Rechazado: firma inválida");
            eventos.save(e);
        });
    }

    private WebhookEvento nuevo(ProveedorPasarela proveedor, String idExterno, String tipo, String cuerpo, boolean firmaValida) {
        WebhookEvento e = new WebhookEvento();
        e.setProveedor(proveedor);
        e.setIdExterno(idExterno);
        e.setTipo(tipo.length() > 40 ? tipo.substring(0, 40) : tipo);
        e.setFirmaValida(firmaValida);
        e.setPayload(leer(cuerpo).map(JsonNode::toString).orElse(null));
        e.setProcesado(false);
        e.setRecibidoEn(Tiempo.ahora());
        return e;
    }

    private Optional<JsonNode> leer(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode n = json.readTree(cuerpo);
            return n != null && n.isObject() ? Optional.of(n) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
