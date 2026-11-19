package pe.alcanfores.reservas.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.ErrorCampo;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.seguridad.SeguridadUtil;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.reservas.cliente.PagosCliente;
import pe.alcanfores.reservas.dto.AdminDtos.AsistenciaSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.CancelacionAdminSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ComprobanteSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ListaReservasRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.PagoInicialSolicitud;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaAdminDetalle;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaFila;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualRespuesta;
import pe.alcanfores.reservas.dto.AdminDtos.ReservaManualSolicitud;
import pe.alcanfores.reservas.dto.Mensajes;
import pe.alcanfores.reservas.mapeador.ReservaMapeador;
import pe.alcanfores.reservas.modelo.Asistencia;
import pe.alcanfores.reservas.modelo.Cancelacion;
import pe.alcanfores.reservas.modelo.CanceladoPor;
import pe.alcanfores.reservas.modelo.EstadoPago;
import pe.alcanfores.reservas.modelo.EstadoReserva;
import pe.alcanfores.reservas.modelo.Origen;
import pe.alcanfores.reservas.modelo.Reserva;
import pe.alcanfores.reservas.modelo.ReservaHistorial;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.servicio.ConstanciaServicio;
import pe.alcanfores.reservas.servicio.ReglasReserva;
import pe.alcanfores.reservas.servicio.ReservaAdminServicio;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pe.alcanfores.reservas.repositorio.ReservaEspecificaciones.base;
import static pe.alcanfores.reservas.repositorio.ReservaEspecificaciones.estado;
import static pe.alcanfores.reservas.repositorio.ReservaEspecificaciones.estadoPago;

@Service
@RequiredArgsConstructor
public class ReservaAdminServicioImpl implements ReservaAdminServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaAdminServicioImpl.class);
    private static final int TAMANIO_MAXIMO = 100;
    private static final Map<String, String> ORDEN = Map.of(
            "fecha", "turno.fecha", "horaInicio", "turno.horaInicio", "cancha", "turno.nombreCancha",
            "cliente", "cliente.apellidos", "codigoReserva", "codigoReserva", "fechaRegistro", "fechaRegistro",
            "montoTotal", "montoTotal", "estado", "estado", "estadoPago", "estadoPago");

    private final ReservaRepositorio reservas;
    private final OperacionesReserva operaciones;
    private final ReservaMapeador mapeador;
    private final PagosCliente pagos;
    private final ConstanciaServicio constancias;
    private final Auditor auditor;
    private final TransactionTemplate transacciones;

    @Override
    @Transactional(readOnly = true)
    public ListaReservasRespuesta listar(Filtro f, Pageable pagina) {
        Specification<Reserva> filtroBase = base(f.fecha(), f.desde(), f.hasta(), f.idCancha(), f.origen(), f.q());
        Page<ReservaFila> page = reservas.findAll(filtroBase.and(estado(f.estado())).and(estadoPago(f.estadoPago())),
                ordenar(pagina)).map(mapeador::fila);
        Map<String, Long> conteos = new LinkedHashMap<>();
        for (EstadoReserva e : EstadoReserva.values()) {
            conteos.put(e.name(), reservas.count(filtroBase.and(estado(e))));
        }
        conteos.put(EstadoPago.EN_REVISION.name(),
                reservas.count(filtroBase.and(estado(EstadoReserva.PENDIENTE)).and(estadoPago(EstadoPago.EN_REVISION))));
        return new ListaReservasRespuesta(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), conteos);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaAdminDetalle detalle(String codigo) {
        return detalleDe(operaciones.buscar(codigo));
    }

    @Override
    public ReservaManualRespuesta registrarManual(ReservaManualSolicitud s) {
        validarPagoInicial(s.pagoInicial());
        UsuarioToken u = SeguridadUtil.requerido();
        PagoInicialSolicitud pago = s.pagoInicial();
        Reserva r = transacciones.execute(estadoTx -> {
            Reserva creada = operaciones.crear(new OperacionesReserva.NuevaReserva(s.turnos(), s.cliente(),
                    Origen.valueOf(s.origen()), pago == null ? null : pago.metodo(), s.nota(), u.id(), u.usuario()));
            validarMonto(pago, creada);
            auditor.registrar("REGISTRAR_RESERVA_MANUAL", "reserva", creada.getCodigoReserva(),
                    creada.getTurno().getNombreCancha() + " · " + creada.getTurno().getFecha() + " "
                            + creada.horarioTexto() + " · " + creada.getOrigen());
            return creada;
        });
        var respuesta = mapeador.creada(r);
        if (pago == null) {
            return new ReservaManualRespuesta(respuesta, false, null);
        }
        ComprobanteSolicitud c = pago.comprobante();
        try {
            pagos.registrarPagoCaja(new PagosCliente.PagoCaja(r.getCodigoReserva(), pago.tipoPago(), pago.metodo(),
                    Dinero.redondear(pago.monto()), vacioANull(pago.codigoOperacion()),
                    new PagosCliente.Comprobante(c.tipo(), vacioANull(c.ruc()), limpiarONull(c.razonSocial()),
                            limpiarONull(c.direccionFiscal())), u.id(), u.usuario()));
            return new ReservaManualRespuesta(respuesta, true, null);
        } catch (NegocioExcepcion e) {
            log.warn("Reserva manual {} creada sin pago inicial: {}", r.getCodigoReserva(), e.getMessage());
            return new ReservaManualRespuesta(respuesta, false, "La reserva quedó registrada como PENDIENTE, pero el pago "
                    + "no se registró (" + e.getMessage() + ") Regístralo con «Cobrar en caja» desde el detalle de la reserva.");
        }
    }

    @Override
    @Transactional
    public ReservaAdminDetalle cancelar(String codigo, CancelacionAdminSolicitud s) {
        Reserva r = operaciones.buscar(codigo);
        if (!r.vigente()) {
            throw NegocioExcepcion.regla("RESERVA_NO_CANCELABLE", "La reserva ya está cancelada.");
        }
        if (r.getAsistencia() != Asistencia.PENDIENTE) {
            throw NegocioExcepcion.regla("RESERVA_NO_CANCELABLE", "Ya se registró la asistencia; no se puede cancelar.");
        }
        boolean devolucion = s.devolucion() == null ? operaciones.correspondeDevolucion(r, Tiempo.ahora()) : s.devolucion();
        UsuarioToken u = SeguridadUtil.requerido();
        Cancelacion c = operaciones.cancelar(r, CanceladoPor.ADMINISTRADOR, u.id(), s.motivo(), devolucion);
        auditor.registrar("CANCELAR_RESERVA", "reserva", r.getCodigoReserva(), Texto.limpiar(s.motivo())
                + (c.isDevolucion() ? " · con devolución de " + Dinero.formato(c.getMontoDevolucion()) : ""));
        return detalleDe(r);
    }

    @Override
    @Transactional
    public ReservaAdminDetalle registrarAsistencia(String codigo, AsistenciaSolicitud s) {
        Reserva r = operaciones.buscar(codigo);
        LocalDateTime ahora = Tiempo.ahora();
        if (r.getEstado() != EstadoReserva.CONFIRMADA) {
            throw NegocioExcepcion.regla("ASISTENCIA_NO_PERMITIDA", "Solo se registra la asistencia de reservas confirmadas.");
        }
        if (!ReglasReserva.puedeMarcarAsistencia(r, ahora)) {
            throw NegocioExcepcion.regla("ASISTENCIA_ANTICIPADA", "La asistencia se registra desde "
                    + ReglasReserva.MINUTOS_ASISTENCIA + " minutos antes del inicio del turno.");
        }
        Asistencia asistencia = Asistencia.valueOf(s.asistencia());
        r.setAsistencia(asistencia);
        operaciones.registrarHito(r, ReservaHistorial.ASISTENCIA,
                asistencia == Asistencia.ASISTIO ? "Asistencia registrada: el cliente asistió." : "El cliente no asistió.");
        operaciones.publicar(Eventos.RESERVA_ASISTENCIA,
                new AsistenciaRegistradaEvento(UUID.randomUUID(), ahora, mapeador.datos(r), asistencia.name()));
        auditor.registrar("REGISTRAR_ASISTENCIA", "reserva", r.getCodigoReserva(), asistencia.name());
        return detalleDe(r);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] constancia(String codigo) {
        Reserva r = operaciones.buscar(codigo);
        return constancias.generar(r, operaciones.cancelacionDe(r));
    }

    private ReservaAdminDetalle detalleDe(Reserva r) {
        return mapeador.detalleAdmin(r, operaciones.cancelacionDe(r), operaciones.historialDe(r), Tiempo.ahora());
    }

    /** Reglas cruzadas del pago inicial que Bean Validation no expresa: operación de Yape/Plin y datos de factura. */
    static void validarPagoInicial(PagoInicialSolicitud p) {
        if (p == null) {
            return;
        }
        List<ErrorCampo> errores = new ArrayList<>();
        boolean billetera = "YAPE".equals(p.metodo()) || "PLIN".equals(p.metodo());
        if (billetera && (p.codigoOperacion() == null || p.codigoOperacion().isBlank())) {
            errores.add(new ErrorCampo("pagoInicial.codigoOperacion", Mensajes.CODIGO_OPERACION));
        }
        ComprobanteSolicitud c = p.comprobante();
        if ("FACTURA".equals(c.tipo())) {
            if (!Documentos.rucParaFactura(c.ruc() == null ? null : c.ruc().trim())) {
                errores.add(new ErrorCampo("pagoInicial.comprobante.ruc", Mensajes.FACTURA));
            }
            if (!largoEntre(c.razonSocial(), 3, 150)) {
                errores.add(new ErrorCampo("pagoInicial.comprobante.razonSocial", Mensajes.FACTURA));
            }
            if (!largoEntre(c.direccionFiscal(), 5, 200)) {
                errores.add(new ErrorCampo("pagoInicial.comprobante.direccionFiscal", Mensajes.FACTURA));
            }
        }
        if (!errores.isEmpty()) {
            throw new NegocioExcepcion(400, "VALIDACION",
                    errores.size() == 1 ? errores.get(0).mensaje() : "Revisa los datos marcados.", errores);
        }
    }

    private static void validarMonto(PagoInicialSolicitud p, Reserva r) {
        if (p == null) {
            return;
        }
        if ("TOTAL".equals(p.tipoPago()) && p.monto().compareTo(r.getMontoTotal()) != 0) {
            throw NegocioExcepcion.validacion("pagoInicial.monto",
                    "El pago total debe ser de " + Dinero.formato(r.getMontoTotal()) + ".");
        }
        if (p.monto().compareTo(r.getMontoTotal()) > 0) {
            throw NegocioExcepcion.validacion("pagoInicial.monto",
                    "El monto no puede superar el saldo de " + Dinero.formato(r.saldo()) + ".");
        }
    }

    private static Pageable ordenar(Pageable p) {
        List<Sort.Order> ordenes = new ArrayList<>();
        for (Sort.Order o : p.getSort()) {
            String propiedad = ORDEN.get(o.getProperty());
            if (propiedad == null) {
                throw NegocioExcepcion.validacion("sort", "No se puede ordenar por «" + o.getProperty() + "».");
            }
            ordenes.add(new Sort.Order(o.getDirection(), propiedad));
            if ("turno.fecha".equals(propiedad)) {
                ordenes.add(new Sort.Order(o.getDirection(), "turno.horaInicio"));
            }
        }
        Sort orden = ordenes.isEmpty() ? Sort.by(Sort.Direction.DESC, "fechaRegistro") : Sort.by(ordenes);
        return PageRequest.of(p.getPageNumber(), Math.min(Math.max(p.getPageSize(), 1), TAMANIO_MAXIMO), orden);
    }

    private static boolean largoEntre(String s, int min, int max) {
        int largo = s == null ? 0 : Texto.limpiar(s).length();
        return largo >= min && largo <= max;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limpiarONull(String s) {
        return s == null || s.isBlank() ? null : Texto.limpiar(s);
    }
}
