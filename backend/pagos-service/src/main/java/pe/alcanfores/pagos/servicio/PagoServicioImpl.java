package pe.alcanfores.pagos.servicio;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PagoRechazadoEvento;
import pe.alcanfores.comun.eventos.PagoRegistradoEvento;
import pe.alcanfores.comun.eventos.PagoValidadoEvento;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.pagos.cliente.ReservaInterna;
import pe.alcanfores.pagos.config.PagosPropiedades;
import pe.alcanfores.pagos.dto.CobroRespuesta;
import pe.alcanfores.pagos.dto.FormularioComprobante;
import pe.alcanfores.pagos.dto.ListadoPagosRespuesta;
import pe.alcanfores.pagos.dto.Mensajes;
import pe.alcanfores.pagos.dto.MetodosPagoRespuesta;
import pe.alcanfores.pagos.dto.PagoClienteRespuesta;
import pe.alcanfores.pagos.dto.PagoDetalleRespuesta;
import pe.alcanfores.pagos.dto.PagoRegistradoRespuesta;
import pe.alcanfores.pagos.dto.PagoResumenRespuesta;
import pe.alcanfores.pagos.dto.SolicitudCaja;
import pe.alcanfores.pagos.dto.SolicitudCajaInterna;
import pe.alcanfores.pagos.evento.BusEventos;
import pe.alcanfores.pagos.mapeador.ComprobanteMapeador;
import pe.alcanfores.pagos.mapeador.PagoMapeador;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.pasarela.simulador.TarjetasPrueba;
import pe.alcanfores.pagos.repositorio.ComprobanteRepositorio;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagoServicioImpl implements PagoServicio {

    static final String MENSAJE_RECIBIDO = "Recibimos tu comprobante. Lo validaremos en unos minutos y te avisaremos por WhatsApp y correo.";

    /** Actor por defecto de la validación automática (bitácora y {@code validado_por}). */
    static final String VALIDADOR_IA = "Canchita (IA)";
    private static final int MAX_LECTURA = 4000;

    private final PagoRepositorio pagos;
    private final ComprobanteRepositorio comprobantes;
    private final DatosFacturacionRepositorio facturacion;
    private final ComprobanteServicio comprobanteServicio;
    private final VerificadorReservas verificador;
    private final AlmacenComprobantes almacen;
    private final ValidadorSolicitudes validador;
    private final ConfiguracionComercio configuracion;
    private final PagosPropiedades propiedades;
    private final BusEventos bus;
    private final TransactionTemplate tx;

    public PagoServicioImpl(PagoRepositorio pagos, ComprobanteRepositorio comprobantes,
                            DatosFacturacionRepositorio facturacion, ComprobanteServicio comprobanteServicio,
                            VerificadorReservas verificador, AlmacenComprobantes almacen, ValidadorSolicitudes validador,
                            ConfiguracionComercio configuracion, PagosPropiedades propiedades, BusEventos bus,
                            PlatformTransactionManager transacciones) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.facturacion = facturacion;
        this.comprobanteServicio = comprobanteServicio;
        this.verificador = verificador;
        this.almacen = almacen;
        this.validador = validador;
        this.configuracion = configuracion;
        this.propiedades = propiedades;
        this.bus = bus;
        this.tx = new TransactionTemplate(transacciones);
    }

    @Override
    public MetodosPagoRespuesta metodos() {
        return new MetodosPagoRespuesta(
                new MetodosPagoRespuesta.Yape(configuracion.yapeNumero(), configuracion.yapeTitular()),
                new MetodosPagoRespuesta.Plin(configuracion.plinNumero()),
                true,
                propiedades.getMercadopago().configurado(),
                TarjetasPrueba.lista().stream()
                        .map(t -> new MetodosPagoRespuesta.TarjetaPrueba(t.numero(), t.marca(), t.resultado())).toList(),
                new MetodosPagoRespuesta.YapePrueba(TarjetasPrueba.YAPE_APRUEBA, TarjetasPrueba.YAPE_RECHAZA),
                TarjetasPrueba.CODIGO_3DS,
                configuracion.adelantoPct());
    }

    @Override
    public PagoRegistradoRespuesta registrarComprobante(FormularioComprobante formulario, byte[] imagen) {
        List<ErrorCampo> errores = new ArrayList<>(validador.errores(formulario));
        almacen.problema(imagen).ifPresent(m -> errores.add(new ErrorCampo("archivo", m)));
        ValidadorSolicitudes.lanzarSiHay(errores);

        ReservaInterna reserva = verificador.paraPagoDelCliente(formulario.codigoReserva(), formulario.dni());
        MetodoPago metodo = MetodoPago.valueOf(formulario.metodo());
        String archivo = almacen.guardar(imagen);
        try {
            return tx.execute(estado -> {
                if (pagos.existsByMetodoAndCodigoOperacionAndEstadoIn(metodo, formulario.codigoOperacion(),
                        EnumSet.of(EstadoValidacion.PENDIENTE, EstadoValidacion.VALIDADO))) {
                    throw operacionRepetida(metodo);
                }
                InstantaneaReserva foto = VerificadorReservas.instantanea(reserva);
                Pago p = Pago.nuevo(reserva.codigoReserva(), foto, metodo, CanalPago.COMPROBANTE, TipoPago.ADELANTO,
                        reserva.montoAdelanto(), EstadoValidacion.PENDIENTE, Tiempo.ahora());
                p.setCodigoOperacion(formulario.codigoOperacion());
                p.setArchivoComprobante(archivo);
                pagos.save(p);
                var df = FacturacionSolicitada.de(formulario.comprobante(), p.getCodigoReserva(), foto,
                        facturacion.findFirstByCodigoReservaOrderByIdDesc(p.getCodigoReserva()));
                df.setIdPago(p.getId());
                facturacion.save(df);
                bus.publicar(Eventos.PAGO_REGISTRADO, new PagoRegistradoEvento(UUID.randomUUID(), Tiempo.ahora(),
                        p.getId(), p.getCodigoReserva(), metodo.name(), CanalPago.COMPROBANTE.name(), p.getMonto(),
                        TipoPago.ADELANTO.name()));
                return new PagoRegistradoRespuesta(p.getId(), EstadoValidacion.PENDIENTE.name(), MENSAJE_RECIBIDO);
            });
        } catch (RuntimeException e) {
            almacen.borrar(archivo);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoClienteRespuesta> pagosDeReserva(String codigoReserva, String dni) {
        List<Pago> lista = pagos.findByCodigoReservaOrderByFechaEnvioAsc(VerificadorReservas.normalizar(codigoReserva));
        if (!lista.isEmpty() && !lista.get(0).getReserva().getClienteDni().equals(dni.trim())) {
            throw NegocioExcepcion.noEncontrado(VerificadorReservas.NO_ENCONTRADA);
        }
        Map<Long, List<Comprobante>> porPago = comprobantesPorPago(lista);
        Map<Long, DatosFacturacion> pedida = lista.isEmpty() ? Map.of()
                : facturacion.findByIdPagoIn(lista.stream().map(Pago::getId).toList()).stream()
                .collect(Collectors.toMap(DatosFacturacion::getIdPago, df -> df, (a, b) -> b));
        return lista.stream()
                .map(p -> PagoMapeador.paraCliente(p, porPago.getOrDefault(p.getId(), List.of()), dni.trim(),
                        pedida.get(p.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ListadoPagosRespuesta listar(String estado, String metodo, String canal, LocalDate desde, LocalDate hasta,
                                       String q, Pageable pagina) {
        EstadoValidacion estadoF = ComprobanteServicioImpl.enumOpcional(EstadoValidacion.class, estado, "estado",
                "Elige un estado válido.");
        MetodoPago metodoF = ComprobanteServicioImpl.enumOpcional(MetodoPago.class, metodo, "metodo", Mensajes.METODO);
        CanalPago canalF = ComprobanteServicioImpl.enumOpcional(CanalPago.class, canal, "canal", "Elige un canal válido.");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw NegocioExcepcion.validacion("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        Specification<Pago> filtro = (raiz, consulta, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (estadoF != null) {
                p.add(cb.equal(raiz.get("estado"), estadoF));
            }
            if (metodoF != null) {
                p.add(cb.equal(raiz.get("metodo"), metodoF));
            }
            if (canalF != null) {
                p.add(cb.equal(raiz.get("canal"), canalF));
            }
            if (desde != null) {
                p.add(cb.greaterThanOrEqualTo(raiz.get("fechaEnvio"), desde.atStartOfDay()));
            }
            if (hasta != null) {
                p.add(cb.lessThan(raiz.get("fechaEnvio"), hasta.plusDays(1).atStartOfDay()));
            }
            String texto = Texto.limpiar(q);
            if (texto != null && !texto.isEmpty()) {
                String patron = "%" + texto.toLowerCase(Locale.ROOT) + "%";
                Path<Object> r = raiz.get("reserva");
                p.add(cb.or(
                        cb.like(cb.lower(raiz.get("codigoReserva")), patron),
                        cb.like(r.get("clienteDni"), patron),
                        cb.like(cb.lower(r.get("clienteNombres")), patron),
                        cb.like(cb.lower(r.get("clienteApellidos")), patron),
                        cb.like(raiz.get("codigoOperacion"), patron)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Pago> resultado = pagos.findAll(filtro, pagina);
        Map<Long, List<Comprobante>> porPago = comprobantesPorPago(resultado.getContent());
        Map<String, Long> conteos = new LinkedHashMap<>();
        for (EstadoValidacion e : EstadoValidacion.values()) {
            conteos.put(e.name(), 0L);
        }
        pagos.contarPorEstado().forEach(f -> conteos.put(((EstadoValidacion) f[0]).name(), (Long) f[1]));
        return new ListadoPagosRespuesta(
                resultado.getContent().stream()
                        .map(p -> PagoMapeador.resumen(p, porPago.getOrDefault(p.getId(), List.of()))).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages(),
                conteos);
    }

    @Override
    public PagoDetalleRespuesta adjuntarFotoCaja(Long id, byte[] imagen) {
        Pago p = pagos.findById(id).orElseThrow(PagoServicioImpl::noExiste);
        if (p.getCanal() != CanalPago.CAJA || p.getMetodo() == MetodoPago.EFECTIVO) {
            throw NegocioExcepcion.regla("FOTO_NO_APLICA",
                    "La foto solo se adjunta a cobros en caja hechos con Yape, Plin o POS.");
        }
        almacen.problema(imagen).ifPresent(m -> {
            throw NegocioExcepcion.validacion("archivo", m);
        });
        String archivo = almacen.guardar(imagen);
        tx.executeWithoutResult(estado -> {
            Pago actual = pagos.findById(id).orElseThrow(PagoServicioImpl::noExiste);
            actual.setArchivoComprobante(archivo);
            pagos.save(actual);
        });
        bus.auditar("ADJUNTAR_FOTO_CAJA", "pago", id, p.getCodigoReserva() + " · " + p.getMetodo());
        return detalle(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoDetalleRespuesta detalle(Long id) {
        Pago p = pagos.findById(id).orElseThrow(PagoServicioImpl::noExiste);
        return PagoMapeador.detalle(p, facturacion.findByIdPago(id).orElse(null), comprobantes.findByIdPagoOrderByIdAsc(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AlmacenComprobantes.Imagen imagen(Long id) {
        return almacen.leer(pagos.findById(id).orElseThrow(PagoServicioImpl::noExiste).getArchivoComprobante());
    }

    @Override
    @Transactional
    public CobroRespuesta validar(Long id) {
        UsuarioToken usuario = SeguridadUtil.requerido();
        Pago p = pagos.bloquear(id).orElseThrow(PagoServicioImpl::noExiste);
        exigirPendiente(p);
        LocalDateTime ahora = Tiempo.ahora();
        p.validar(ahora, usuario.id(), usuario.usuario());
        Comprobante c = comprobanteServicio.emitirParaPago(p, ahora, true);
        anunciarValidado(p);
        bus.auditar("VALIDAR_PAGO", "pago", p.getId(),
                p.getCodigoReserva() + " · " + p.getMetodo().texto() + " " + Dinero.formato(p.getMonto()) + " · " + c.serieNumero());
        return new CobroRespuesta(p.getId(), p.getEstado().name(), p.getMonto(), ComprobanteMapeador.enlaceAdmin(c));
    }

    @Override
    @Transactional
    public CobroRespuesta validarPorIa(Long id, String lectura, String validadoPor) {
        String actor = Texto.limpiar(validadoPor);
        actor = actor == null || actor.isBlank() ? VALIDADOR_IA : actor;
        Pago p = pagos.bloquear(id).orElseThrow(PagoServicioImpl::noExiste);
        if (p.getEstado() == EstadoValidacion.VALIDADO) {
            List<Comprobante> emitidos = comprobantes.findByIdPagoOrderByIdAsc(id);
            return new CobroRespuesta(p.getId(), p.getEstado().name(), p.getMonto(),
                    emitidos.isEmpty() ? null : ComprobanteMapeador.enlaceAdmin(emitidos.get(0)));
        }
        if (p.getEstado() != EstadoValidacion.PENDIENTE || p.getCanal() != CanalPago.COMPROBANTE) {
            throw NegocioExcepcion.regla("PAGO_NO_EN_REVISION",
                    "Este pago no está en revisión (estado: " + p.getEstado().name().toLowerCase(Locale.ROOT) + ").");
        }
        LocalDateTime ahora = Tiempo.ahora();
        p.validar(ahora, null, actor);
        p.setLecturaIa(lectura == null ? null : lectura.substring(0, Math.min(lectura.length(), MAX_LECTURA)));
        Comprobante c = comprobanteServicio.emitirParaPago(p, ahora, true);
        anunciarValidado(p);
        bus.auditarComo(actor, "SISTEMA", "VALIDAR_PAGO_IA", "pago", p.getId(),
                p.getCodigoReserva() + " · " + p.getMetodo().texto() + " " + Dinero.formato(p.getMonto()) + " · " + c.serieNumero());
        return new CobroRespuesta(p.getId(), p.getEstado().name(), p.getMonto(), ComprobanteMapeador.enlaceAdmin(c));
    }

    @Override
    @Transactional
    public PagoResumenRespuesta rechazar(Long id, String observacion) {
        String limpia = Texto.limpiar(observacion);
        if (limpia == null || limpia.length() < 10 || limpia.length() > 255) {
            throw NegocioExcepcion.validacion("observacion", Mensajes.MOTIVO);
        }
        UsuarioToken usuario = SeguridadUtil.requerido();
        Pago p = pagos.bloquear(id).orElseThrow(PagoServicioImpl::noExiste);
        exigirPendiente(p);
        p.setEstado(EstadoValidacion.RECHAZADO);
        p.setObservacion(limpia);
        p.setFechaValidacion(Tiempo.ahora());
        p.setIdAdministrador(usuario.id());
        p.setValidadoPor(usuario.usuario());
        bus.publicar(Eventos.PAGO_RECHAZADO, new PagoRechazadoEvento(UUID.randomUUID(), Tiempo.ahora(), p.getId(),
                p.getCodigoReserva(), limpia, usuario.usuario()));
        bus.auditar("RECHAZAR_PAGO", "pago", p.getId(), p.getCodigoReserva() + " · " + limpia);
        return PagoMapeador.resumen(p, List.of());
    }

    @Override
    public CobroRespuesta cobrarEnCaja(SolicitudCaja solicitud) {
        UsuarioToken usuario = SeguridadUtil.requerido();
        ReservaInterna reserva = verificador.paraCaja(solicitud.codigoReserva());
        return registrarCobroCaja(solicitud, reserva, usuario.id(), usuario.usuario(), false);
    }

    @Override
    public CobroRespuesta cobrarEnCajaInterno(SolicitudCajaInterna solicitud) {
        ReservaInterna reserva = solicitud.reserva() != null ? ReservaInterna.de(solicitud.reserva())
                : verificador.paraCaja(solicitud.codigoReserva());
        String actor = solicitud.registradoPorUsuario() == null || solicitud.registradoPorUsuario().isBlank()
                ? "Recepción" : solicitud.registradoPorUsuario().trim();
        return registrarCobroCaja(solicitud.cobro(), reserva, solicitud.registradoPor(), actor, true);
    }

    private CobroRespuesta registrarCobroCaja(SolicitudCaja s, ReservaInterna reserva, Long idAdministrador,
                                              String actor, boolean interno) {
        MetodoPago metodo = MetodoPago.valueOf(s.metodo());
        TipoPago tipo = TipoPago.valueOf(s.tipoPago());
        if ((metodo == MetodoPago.YAPE || metodo == MetodoPago.PLIN) && s.codigoOperacion() == null) {
            throw NegocioExcepcion.validacion("codigoOperacion", Mensajes.OPERACION);
        }
        if (ReservaInterna.CANCELADA.equals(reserva.estado())) {
            throw NegocioExcepcion.regla("RESERVA_CANCELADA", "La reserva está cancelada; no se puede cobrar.");
        }
        String codigo = reserva.codigoReserva();
        return tx.execute(estado -> {
            // Lo cobrado lo sabe pagos-service (dueño de los pagos): así no depende de que reservas ya procesó el evento.
            BigDecimal pagado = pagos.sumarPorEstado(codigo, EstadoValidacion.VALIDADO);
            BigDecimal saldo = reserva.montoTotal().subtract(pagado);
            BigDecimal monto = s.monto().setScale(2, RoundingMode.HALF_UP);
            validarCobro(tipo, monto, saldo, pagado.signum() == 0, reserva.montoAdelanto());
            if (tipo != TipoPago.SALDO && pagos.existsByCodigoReservaAndEstado(codigo, EstadoValidacion.PENDIENTE)) {
                throw NegocioExcepcion.conflicto("PAGO_EN_REVISION", "Hay un comprobante de Yape/Plin por validar en "
                        + "esta reserva. Valídalo o recházalo antes de cobrar en caja.");
            }
            LocalDateTime ahora = Tiempo.ahora();
            InstantaneaReserva foto = VerificadorReservas.instantanea(reserva);
            Pago p = Pago.nuevo(codigo, foto, metodo, CanalPago.CAJA, tipo, monto, EstadoValidacion.PENDIENTE, ahora);
            p.setCodigoOperacion(s.codigoOperacion());
            p.validar(ahora, idAdministrador, actor);
            pagos.save(p);
            var df = FacturacionSolicitada.de(s.comprobante(), codigo, foto, facturacion.findFirstByCodigoReservaOrderByIdDesc(codigo));
            df.setIdPago(p.getId());
            facturacion.save(df);
            Comprobante c = comprobanteServicio.emitirParaPago(p, ahora, true);
            anunciarValidado(p);
            String detalle = codigo + " · " + tipo.name() + " " + metodo.texto() + " " + Dinero.formato(monto) + " · " + c.serieNumero();
            if (interno) {
                bus.auditarComo(actor, "SISTEMA", "COBRAR_CAJA", "pago", p.getId(), detalle);
            } else {
                bus.auditar("COBRAR_CAJA", "pago", p.getId(), detalle);
            }
            return new CobroRespuesta(p.getId(), p.getEstado().name(), p.getMonto(), ComprobanteMapeador.enlaceAdmin(c));
        });
    }

    static void validarCobro(TipoPago tipo, BigDecimal monto, BigDecimal saldo, boolean sinPagos, BigDecimal adelanto) {
        if (saldo.signum() <= 0) {
            throw NegocioExcepcion.conflicto("SIN_SALDO", "Esta reserva ya está pagada por completo.");
        }
        if (monto.compareTo(saldo) > 0) {
            throw NegocioExcepcion.validacion("monto", "El monto no puede superar el saldo de " + Dinero.formato(saldo) + ".");
        }
        switch (tipo) {
            case SALDO -> {
                if (sinPagos) {
                    throw NegocioExcepcion.regla("TIPO_PAGO_INVALIDO",
                            "Esta reserva aún no tiene adelanto: registra el adelanto o el pago total.");
                }
            }
            case ADELANTO -> {
                exigirSinPagos(sinPagos);
                if (adelanto != null && monto.compareTo(adelanto) < 0) {
                    throw NegocioExcepcion.validacion("monto", "El adelanto mínimo es " + Dinero.formato(adelanto) + ".");
                }
            }
            case TOTAL -> {
                exigirSinPagos(sinPagos);
                if (monto.compareTo(saldo) != 0) {
                    throw NegocioExcepcion.validacion("monto", "Para el pago total el monto debe ser " + Dinero.formato(saldo) + ".");
                }
            }
        }
    }

    private static void exigirSinPagos(boolean sinPagos) {
        if (!sinPagos) {
            throw NegocioExcepcion.regla("TIPO_PAGO_INVALIDO", "Esta reserva ya tiene un pago registrado: cobra el saldo.");
        }
    }

    private void anunciarValidado(Pago p) {
        bus.publicar(Eventos.PAGO_VALIDADO, new PagoValidadoEvento(UUID.randomUUID(), Tiempo.ahora(), p.getId(),
                p.getCodigoReserva(), p.getMetodo().name(), p.getCanal().name(), p.getMonto(), p.getTipoPago().name(),
                p.getValidadoPor()));
    }

    /** 409 con el campo marcado: el front lo pinta en «Número de operación» sin conocer el código. */
    static NegocioExcepcion operacionRepetida(MetodoPago metodo) {
        String mensaje = "Ese número de operación ya fue registrado en otro pago. Revisa tu constancia de " + metodo.texto() + ".";
        return new NegocioExcepcion(409, "OPERACION_REPETIDA", mensaje, List.of(new ErrorCampo("codigoOperacion", mensaje)));
    }

    private static void exigirPendiente(Pago p) {
        if (p.getEstado() != EstadoValidacion.PENDIENTE) {
            throw NegocioExcepcion.conflicto("PAGO_YA_PROCESADO",
                    "Este pago ya no está pendiente (estado: " + p.getEstado().name().toLowerCase(Locale.ROOT) + ").");
        }
    }

    private Map<Long, List<Comprobante>> comprobantesPorPago(List<Pago> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return comprobantes.findByIdPagoInOrderByIdAsc(lista.stream().map(Pago::getId).toList()).stream()
                .collect(Collectors.groupingBy(Comprobante::getIdPago));
    }

    private static NegocioExcepcion noExiste() {
        return NegocioExcepcion.noEncontrado("No encontramos ese pago.");
    }
}
