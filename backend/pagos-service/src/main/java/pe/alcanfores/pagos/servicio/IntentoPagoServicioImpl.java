package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.config.AlcanforesPropiedades;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.CodigoReserva;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PagoMp;
import pe.alcanfores.pagos.cliente.MercadoPagoCliente.PreferenciaMp;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.config.PagosPropiedades;
import pe.alcanfores.pagos.dto.CheckoutRespuesta;
import pe.alcanfores.pagos.dto.ComprobanteEnlace;
import pe.alcanfores.pagos.dto.DatosComprobanteSolicitud;
import pe.alcanfores.pagos.dto.EstadoIntentoRespuesta;
import pe.alcanfores.pagos.dto.IntentoCreadoRespuesta;
import pe.alcanfores.pagos.dto.PreferenciaRespuesta;
import pe.alcanfores.pagos.dto.SolicitudIntento;
import pe.alcanfores.pagos.mapeador.ComprobanteMapeador;
import pe.alcanfores.pagos.modelo.EstadoIntento;
import pe.alcanfores.pagos.modelo.IntentoPago;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.ProveedorPasarela;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.repositorio.ComprobanteRepositorio;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.IntentoPagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ConceptoReserva;

import java.math.BigDecimal;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IntentoPagoServicioImpl implements IntentoPagoServicio {

    private static final Logger log = LoggerFactory.getLogger(IntentoPagoServicioImpl.class);
    private static final SecureRandom AZAR = new SecureRandom();
    private static final DateTimeFormatter FECHA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");
    private static final Set<EstadoIntento> ABIERTOS = EnumSet.of(EstadoIntento.CREADO, EstadoIntento.REQUIERE_3DS,
            EstadoIntento.RECHAZADO);
    static final String NO_ENCONTRADO = "No encontramos ese pago en línea.";

    private final IntentoPagoRepositorio intentos;
    private final DatosFacturacionRepositorio facturacion;
    private final ComprobanteRepositorio comprobantes;
    private final VerificadorReservas verificador;
    private final AprobacionEnLinea aprobacion;
    private final MercadoPagoCliente mercadoPago;
    private final ConfiguracionComercio configuracion;
    private final PagosPropiedades propiedades;
    private final AlcanforesPropiedades generales;
    private final TransactionTemplate tx;

    public IntentoPagoServicioImpl(IntentoPagoRepositorio intentos, DatosFacturacionRepositorio facturacion,
                                   ComprobanteRepositorio comprobantes, VerificadorReservas verificador,
                                   AprobacionEnLinea aprobacion, MercadoPagoCliente mercadoPago,
                                   ConfiguracionComercio configuracion, PagosPropiedades propiedades,
                                   AlcanforesPropiedades generales, PlatformTransactionManager transacciones) {
        this.intentos = intentos;
        this.facturacion = facturacion;
        this.comprobantes = comprobantes;
        this.verificador = verificador;
        this.aprobacion = aprobacion;
        this.mercadoPago = mercadoPago;
        this.configuracion = configuracion;
        this.propiedades = propiedades;
        this.generales = generales;
        this.tx = new TransactionTemplate(transacciones);
    }

    @Override
    public IntentoCreadoRespuesta crearIntentoBeta(SolicitudIntento s) {
        ReservaInterna r = verificador.paraPagoDelCliente(s.codigoReserva(), s.dni());
        TipoPago tipo = TipoPago.valueOf(s.tipoPago());
        IntentoPago i = tx.execute(estado -> {
            IntentoPago nuevo = nuevoIntento(r, ProveedorPasarela.BETA, tipo);
            nuevo.setUrlCheckout("/pasarela/" + nuevo.getReferencia());
            return guardar(nuevo, s.comprobante());
        });
        return new IntentoCreadoRespuesta(i.getReferencia(), i.getUrlCheckout(), i.getExpiraEn());
    }

    @Override
    public PreferenciaRespuesta crearPreferenciaMercadoPago(SolicitudIntento s) {
        PagosPropiedades.MercadoPago mp = propiedades.getMercadopago();
        if (!mp.configurado()) {
            throw NegocioExcepcion.conflicto("MP_NO_CONFIGURADO",
                    "Mercado Pago no está disponible en este momento. Paga con Yape, Plin o tarjeta.");
        }
        ReservaInterna r = verificador.paraPagoDelCliente(s.codigoReserva(), s.dni());
        TipoPago tipo = TipoPago.valueOf(s.tipoPago());
        IntentoPago borrador = nuevoIntento(r, ProveedorPasarela.MERCADO_PAGO, tipo);
        PreferenciaMp preferencia = mercadoPago.crearPreferencia(cuerpoPreferencia(borrador, r), borrador.getReferencia());
        String url = mp.sandbox() && preferencia.sandboxInitPoint() != null
                ? preferencia.sandboxInitPoint() : preferencia.initPoint();
        borrador.setIdExterno(preferencia.id());
        borrador.setUrlCheckout(url);
        IntentoPago i = tx.execute(estado -> guardar(borrador, s.comprobante()));
        return new PreferenciaRespuesta(i.getReferencia(), url, mp.sandbox(), i.getExpiraEn());
    }

    Map<String, Object> cuerpoPreferencia(IntentoPago i, ReservaInterna r) {
        String referencia = i.getReferencia();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", i.getCodigoReserva());
        item.put("title", "Reserva " + i.getCodigoReserva() + " · " + configuracion.nombreComplejo());
        item.put("description", ConceptoReserva.lineaComprobante(i.getReserva(), i.getTipoPago()));
        item.put("category_id", "services");
        item.put("quantity", 1);
        item.put("currency_id", "PEN");
        item.put("unit_price", i.getMonto());

        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("name", r.cliente().nombres());
        pagador.put("surname", r.cliente().apellidos());
        if (r.cliente().correo() != null) {
            pagador.put("email", r.cliente().correo());
        }
        pagador.put("identification", Map.of("type", "DNI", "number", r.cliente().dni()));

        String retorno = generales.getFrontUrl().split(",")[0].trim() + "/pago/resultado?ref=" + referencia;
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("payer", pagador);
        cuerpo.put("external_reference", referencia);
        cuerpo.put("statement_descriptor", "ALCANFORES");
        cuerpo.put("back_urls", Map.of("success", retorno, "failure", retorno, "pending", retorno));
        cuerpo.put("expires", true);
        cuerpo.put("expiration_date_from", i.getCreadoEn().atZone(Tiempo.ZONA).format(FECHA_MP));
        cuerpo.put("expiration_date_to", i.getExpiraEn().atZone(Tiempo.ZONA).format(FECHA_MP));
        cuerpo.put("metadata", Map.of("codigo_reserva", i.getCodigoReserva(), "tipo_pago", i.getTipoPago().name()));
        // Mercado Pago rechaza auto_return y no puede notificar a direcciones locales.
        if (!esLocal(retorno)) {
            cuerpo.put("auto_return", "approved");
        }
        String publica = generales.getPublicApiUrl();
        if (!esLocal(publica)) {
            cuerpo.put("notification_url", publica + "/api/webhooks/mercadopago");
        }
        return cuerpo;
    }

    static boolean esLocal(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null || HOSTS_LOCALES.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutRespuesta checkout(String referencia) {
        IntentoPago i = intentos.findByReferencia(referencia)
                .filter(x -> x.getProveedor() == ProveedorPasarela.BETA)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO));
        return new CheckoutRespuesta(i.getReferencia(), configuracion.nombreComplejo(),
                ConceptoReserva.lineaComprobante(i.getReserva(), i.getTipoPago()), i.getMonto(), "PEN",
                estadoVisible(i).name(), i.getExpiraEn(), i.getCodigoReserva(), i.getMotivoRechazo());
    }

    @Override
    public EstadoIntentoRespuesta estado(String referencia) {
        IntentoPago i = intentos.findByReferencia(referencia).orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO));
        if (i.getProveedor() == ProveedorPasarela.MERCADO_PAGO && i.getEstado() != EstadoIntento.APROBADO
                && propiedades.getMercadopago().configurado()) {
            sincronizarConMercadoPago(referencia);
            i = intentos.findByReferencia(referencia).orElseThrow();
        }
        ComprobanteEnlace comprobante = i.getIdPago() == null ? null
                : ComprobanteMapeador.principal(comprobantes.findByIdPagoOrderByIdAsc(i.getIdPago()))
                .map(c -> ComprobanteMapeador.enlaceCliente(c, null)).orElse(null);
        return new EstadoIntentoRespuesta(i.getReferencia(), i.getProveedor().name(), estadoVisible(i).name(),
                i.getTipoPago().name(), i.getMonto(), i.getCodigoReserva(), i.getMotivoRechazo(), i.getExpiraEn(),
                i.getIdPago(), comprobante);
    }

    /** Botón «Ya pagué, verificar»: pregunta a Mercado Pago por los pagos con esta referencia. */
    void sincronizarConMercadoPago(String referencia) {
        try {
            List<PagoMp> encontrados = mercadoPago.buscarPorReferencia(referencia);
            Optional<PagoMp> aprobado = encontrados.stream().filter(PagoMp::aprobado).findFirst();
            if (aprobado.isPresent()) {
                aprobacion.aprobar(referencia, aprobacionDe(aprobado.get()));
            } else if (!encontrados.isEmpty() && encontrados.get(0).rechazado()) {
                aprobacion.rechazar(referencia, motivoMercadoPago(encontrados.get(0).detalleEstado()));
            }
        } catch (NegocioExcepcion e) {
            log.warn("No se pudo verificar en Mercado Pago la referencia {}: {}", referencia, e.getMessage());
        }
    }

    public static AprobacionEnLinea.Aprobacion aprobacionDe(PagoMp pago) {
        String marca = pago.medio() == null ? null : pago.medio().substring(0, 1).toUpperCase(Locale.ROOT) + pago.medio().substring(1);
        return new AprobacionEnLinea.Aprobacion(MetodoPago.MERCADO_PAGO, marca, pago.ultimos4(),
                pago.codigoAutorizacion(), pago.id());
    }

    public static String motivoMercadoPago(String detalle) {
        if (detalle == null) {
            return "Mercado Pago rechazó el pago.";
        }
        return switch (detalle) {
            case "cc_rejected_insufficient_amount" -> "Fondos insuficientes.";
            case "cc_rejected_bad_filled_security_code" -> "El código de seguridad no es correcto.";
            case "cc_rejected_bad_filled_date" -> "La fecha de vencimiento no es correcta.";
            case "cc_rejected_call_for_authorize" -> "Tu banco debe autorizar el pago.";
            case "cc_rejected_high_risk" -> "Mercado Pago rechazó el pago por seguridad.";
            default -> "Mercado Pago rechazó el pago.";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public IntentoPago abiertoParaCobro(String referencia) {
        IntentoPago i = intentos.findByReferencia(referencia)
                .filter(x -> x.getProveedor() == ProveedorPasarela.BETA)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO));
        if (i.getEstado() == EstadoIntento.APROBADO) {
            throw NegocioExcepcion.conflicto("INTENTO_PAGADO", "Este pago ya fue aprobado.");
        }
        if (estadoVisible(i) == EstadoIntento.EXPIRADO) {
            throw NegocioExcepcion.conflicto("INTENTO_EXPIRADO",
                    "El tiempo para pagar venció. Vuelve a tu reserva y genera un nuevo pago.");
        }
        return i;
    }

    @Override
    @Transactional
    public void marcarRequiere3ds(String referencia) {
        intentos.bloquear(referencia).filter(i -> ABIERTOS.contains(i.getEstado())).ifPresent(i -> {
            i.setEstado(EstadoIntento.REQUIERE_3DS);
            i.setActualizadoEn(Tiempo.ahora());
        });
    }

    @Override
    @Transactional
    public int expirarVencidos() {
        LocalDateTime ahora = Tiempo.ahora();
        List<IntentoPago> vencidos = intentos.findByEstadoInAndExpiraEnBefore(ABIERTOS, ahora);
        vencidos.forEach(i -> {
            i.setEstado(EstadoIntento.EXPIRADO);
            if (i.getMotivoRechazo() == null) {
                i.setMotivoRechazo("Venció el tiempo para pagar.");
            }
            i.setActualizadoEn(ahora);
        });
        return vencidos.size();
    }

    private IntentoPago nuevoIntento(ReservaInterna r, ProveedorPasarela proveedor, TipoPago tipo) {
        BigDecimal monto = tipo == TipoPago.ADELANTO ? r.montoAdelanto() : r.montoTotal().subtract(r.pagado());
        LocalDateTime ahora = Tiempo.ahora();
        IntentoPago i = new IntentoPago();
        i.setReferencia(nuevaReferencia());
        i.setCodigoReserva(r.codigoReserva());
        i.setProveedor(proveedor);
        i.setTipoPago(tipo);
        i.setMonto(monto.setScale(2, java.math.RoundingMode.HALF_UP));
        i.setEstado(EstadoIntento.CREADO);
        i.setCreadoEn(ahora);
        i.setActualizadoEn(ahora);
        i.setExpiraEn(ahora.plusMinutes(propiedades.getPasarela().getMinutosExpiracion()));
        i.setReserva(VerificadorReservas.instantanea(r));
        return i;
    }

    /** Guarda el intento y su facturación; los intentos abiertos anteriores de la reserva se cierran (evita cobrar dos veces). */
    private IntentoPago guardar(IntentoPago i, DatosComprobanteSolicitud comprobante) {
        intentos.findByCodigoReservaAndEstadoIn(i.getCodigoReserva(), ABIERTOS).forEach(anterior -> {
            anterior.setEstado(EstadoIntento.EXPIRADO);
            anterior.setMotivoRechazo("Se generó un nuevo pago para esta reserva.");
            anterior.setActualizadoEn(Tiempo.ahora());
        });
        intentos.save(i);
        var df = FacturacionSolicitada.de(comprobante, i.getCodigoReserva(), i.getReserva());
        df.setReferenciaIntento(i.getReferencia());
        facturacion.save(df);
        return i;
    }

    /** Un intento abierto con el plazo vencido se muestra EXPIRADO aunque la tarea aún no lo haya marcado. */
    private static EstadoIntento estadoVisible(IntentoPago i) {
        if (ABIERTOS.contains(i.getEstado()) && !i.getExpiraEn().isAfter(Tiempo.ahora())) {
            return EstadoIntento.EXPIRADO;
        }
        return i.getEstado();
    }

    private String nuevaReferencia() {
        String alfabeto = CodigoReserva.ALFABETO;
        String referencia;
        do {
            StringBuilder sb = new StringBuilder("ALP-");
            for (int k = 0; k < 10; k++) {
                sb.append(alfabeto.charAt(AZAR.nextInt(alfabeto.length())));
            }
            referencia = sb.toString();
        } while (intentos.existsByReferencia(referencia));
        return referencia;
    }
}
