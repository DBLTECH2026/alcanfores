package pe.alcanfores.pagos.servicio;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.ComprobanteEmitidoEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.dto.ComprobanteRespuesta;
import pe.alcanfores.pagos.evento.BusEventos;
import pe.alcanfores.pagos.mapeador.ComprobanteMapeador;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoComprobante;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.repositorio.ComprobanteRepositorio;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ArmadorComprobante;
import pe.alcanfores.pagos.servicio.documento.DocumentoComprobante;
import pe.alcanfores.pagos.servicio.documento.GeneradorPdfComprobante;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComprobanteServicioImpl implements ComprobanteServicio {

    private static final Pattern SERIE_NUMERO = Pattern.compile("^(B001|F001|BC01|FC01)-(\\d{1,8})$");
    static final String NO_ENCONTRADO_CLIENTE = "No encontramos ese comprobante para la reserva y el DNI indicados.";

    private final ComprobanteRepositorio comprobantes;
    private final PagoRepositorio pagos;
    private final DatosFacturacionRepositorio facturacion;
    private final NumeracionComprobantes numeracion;
    private final ArmadorComprobante armador;
    private final GeneradorPdfComprobante generadorPdf;
    private final ConfiguracionComercio configuracion;
    private final BusEventos bus;

    public ComprobanteServicioImpl(ComprobanteRepositorio comprobantes, PagoRepositorio pagos,
                                   DatosFacturacionRepositorio facturacion, NumeracionComprobantes numeracion,
                                   ArmadorComprobante armador, GeneradorPdfComprobante generadorPdf,
                                   ConfiguracionComercio configuracion, BusEventos bus) {
        this.comprobantes = comprobantes;
        this.pagos = pagos;
        this.facturacion = facturacion;
        this.numeracion = numeracion;
        this.armador = armador;
        this.generadorPdf = generadorPdf;
        this.configuracion = configuracion;
        this.bus = bus;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Comprobante emitirParaPago(Pago pago, LocalDateTime fecha, boolean publicarEvento) {
        DatosFacturacion df = facturacion.findByIdPago(pago.getId()).orElseGet(() -> boletaAlCliente(pago));
        String serie = ArmadorComprobante.seriePara(df.getTipo());
        Comprobante c = comprobantes.save(armador.armar(pago, df, configuracion.emisor(), serie,
                numeracion.siguiente(serie), fecha));
        if (publicarEvento) {
            anunciar(c, pago);
        }
        return c;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Comprobante emitirNotaCredito(Comprobante original, String codigoMotivo, String motivo, LocalDateTime fecha,
                                         boolean publicarEvento) {
        if (original.getTipo() == TipoComprobante.NOTA_CREDITO) {
            throw NegocioExcepcion.regla("NOTA_NO_ANULABLE", "Una nota de crédito no se anula con otra nota de crédito.");
        }
        if (original.getEstado() == EstadoComprobante.ANULADO) {
            throw NegocioExcepcion.conflicto("COMPROBANTE_ANULADO", "Este comprobante ya fue anulado con una nota de crédito.");
        }
        String serie = ArmadorComprobante.serieNotaPara(original);
        Comprobante nc = comprobantes.save(armador.armarNotaCredito(original, serie, numeracion.siguiente(serie), fecha,
                codigoMotivo, Texto.limpiar(motivo)));
        original.setEstado(EstadoComprobante.ANULADO);
        if (publicarEvento) {
            anunciar(nc, pagos.findById(original.getIdPago()).orElse(null));
        }
        return nc;
    }

    @Override
    @Transactional
    public ComprobanteRespuesta anular(Long id, String motivo) {
        String limpio = Texto.limpiar(motivo);
        if (limpio == null || limpio.length() < 10 || limpio.length() > 255) {
            throw NegocioExcepcion.validacion("motivo", "Cuéntanos el motivo (mínimo 10 caracteres).");
        }
        Comprobante original = comprobantes.bloquear(id).orElseThrow(ComprobanteServicioImpl::noExiste);
        Comprobante nc = emitirNotaCredito(original, MOTIVO_ANULACION, limpio, Tiempo.ahora(), true);
        bus.auditar("ANULAR_COMPROBANTE", "comprobante", original.getId(),
                original.serieNumero() + " anulado con " + nc.serieNumero() + " · " + limpio);
        return ComprobanteMapeador.respuesta(nc, original);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<ComprobanteRespuesta> listar(String tipo, String estado, LocalDate desde, LocalDate hasta,
                                                        String q, Pageable pagina) {
        TipoComprobante tipoFiltro = enumOpcional(TipoComprobante.class, tipo, "tipo",
                "Elige boleta, factura o nota de crédito.");
        EstadoComprobante estadoFiltro = enumOpcional(EstadoComprobante.class, estado, "estado",
                "Elige emitido o anulado.");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        Specification<Comprobante> filtro = (raiz, consulta, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tipoFiltro != null) {
                p.add(cb.equal(raiz.get("tipo"), tipoFiltro));
            }
            if (estadoFiltro != null) {
                p.add(cb.equal(raiz.get("estado"), estadoFiltro));
            }
            if (desde != null) {
                p.add(cb.greaterThanOrEqualTo(raiz.get("fechaEmision"), desde.atStartOfDay()));
            }
            if (hasta != null) {
                p.add(cb.lessThan(raiz.get("fechaEmision"), hasta.plusDays(1).atStartOfDay()));
            }
            String texto = Texto.limpiar(q);
            if (texto != null && !texto.isEmpty()) {
                String patron = "%" + texto.toLowerCase(Locale.ROOT) + "%";
                List<Predicate> o = new ArrayList<>();
                o.add(cb.like(cb.lower(raiz.get("clienteNombre")), patron));
                o.add(cb.like(raiz.get("clienteNumDoc"), patron));
                o.add(cb.like(cb.lower(raiz.get("codigoReserva")), patron));
                Matcher m = SERIE_NUMERO.matcher(texto.toUpperCase(Locale.ROOT));
                if (m.matches()) {
                    o.add(cb.and(cb.equal(raiz.get("serie"), m.group(1)),
                            cb.equal(raiz.get("correlativo"), Integer.parseInt(m.group(2)))));
                }
                p.add(cb.or(o.toArray(Predicate[]::new)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaRespuesta.de(comprobantes.findAll(filtro, pagina), c -> ComprobanteMapeador.respuesta(c,
                c.getIdReferencia() == null ? null : comprobantes.findById(c.getIdReferencia()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public ComprobanteRespuesta obtener(Long id) {
        Comprobante c = comprobantes.findById(id).orElseThrow(ComprobanteServicioImpl::noExiste);
        return ComprobanteMapeador.respuesta(c, referencia(c));
    }

    @Override
    @Transactional(readOnly = true)
    public Archivo pdf(Long id) {
        return pdfDe(comprobantes.findById(id).orElseThrow(ComprobanteServicioImpl::noExiste));
    }

    @Override
    @Transactional(readOnly = true)
    public Archivo xml(Long id) {
        return xmlDe(comprobantes.findById(id).orElseThrow(ComprobanteServicioImpl::noExiste));
    }

    @Override
    @Transactional(readOnly = true)
    public Archivo pdfParaCliente(String serieNumero, String codigoReserva, String dni) {
        return pdfDe(delCliente(serieNumero, codigoReserva, dni));
    }

    @Override
    @Transactional(readOnly = true)
    public Archivo xmlParaCliente(String serieNumero, String codigoReserva, String dni) {
        return xmlDe(delCliente(serieNumero, codigoReserva, dni));
    }

    private Comprobante delCliente(String serieNumero, String codigoReserva, String dni) {
        Matcher m = SERIE_NUMERO.matcher(serieNumero == null ? "" : serieNumero.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) {
            throw NegocioExcepcion.noEncontrado(NO_ENCONTRADO_CLIENTE);
        }
        Comprobante c = comprobantes.findBySerieAndCorrelativo(m.group(1), Integer.parseInt(m.group(2)))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO_CLIENTE));
        Pago pago = pagos.findById(c.getIdPago()).orElseThrow(() -> NegocioExcepcion.noEncontrado(NO_ENCONTRADO_CLIENTE));
        boolean coincide = c.getCodigoReserva().equalsIgnoreCase(codigoReserva.trim())
                && pago.getReserva().getClienteDni().equals(dni.trim());
        if (!coincide) {
            throw NegocioExcepcion.noEncontrado(NO_ENCONTRADO_CLIENTE);
        }
        return c;
    }

    private Archivo pdfDe(Comprobante c) {
        String formaPago = pagos.findById(c.getIdPago()).map(p -> "Contado · " + p.getMetodo().texto()).orElse("Contado");
        byte[] bytes = generadorPdf.generar(new DocumentoComprobante(c, referencia(c), formaPago));
        return new Archivo(c.serieNumero() + ".pdf", "application/pdf", bytes);
    }

    private Archivo xmlDe(Comprobante c) {
        String contenido = armador.xml(c, referencia(c));
        return new Archivo(c.getEmisorRuc() + "-" + c.getCodigoTipo() + "-" + c.serieNumero() + ".xml",
                "application/xml", contenido.getBytes(StandardCharsets.UTF_8));
    }

    private Comprobante referencia(Comprobante c) {
        return c.getIdReferencia() == null ? null : comprobantes.findById(c.getIdReferencia()).orElse(null);
    }

    private DatosFacturacion boletaAlCliente(Pago pago) {
        DatosFacturacion df = new DatosFacturacion();
        df.setCodigoReserva(pago.getCodigoReserva());
        df.setIdPago(pago.getId());
        df.setTipo(TipoComprobante.BOLETA);
        df.setTipoDoc(DatosFacturacion.DOC_DNI);
        df.setNumDoc(pago.getReserva().getClienteDni());
        df.setNombre(pago.getReserva().nombreCompleto());
        return facturacion.save(df);
    }

    private void anunciar(Comprobante c, Pago pago) {
        bus.publicar(Eventos.COMPROBANTE_EMITIDO, new ComprobanteEmitidoEvento(UUID.randomUUID(), Tiempo.ahora(),
                c.getId(), c.getCodigoReserva(), c.getTipo().name(), c.serieNumero(), c.getTotal(),
                pago == null ? null : pago.getReserva().contacto()));
    }

    private static NegocioExcepcion noExiste() {
        return NegocioExcepcion.noEncontrado("No encontramos ese comprobante.");
    }

    static <E extends Enum<E>> E enumOpcional(Class<E> tipo, String valor, String campo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw NegocioExcepcion.validacion(campo, mensaje);
        }
    }
}
