package pe.alcanfores.pagos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.pagos.cliente.ReservaSemilla;
import pe.alcanfores.pagos.cliente.ReservasCliente;
import pe.alcanfores.pagos.modelo.CanalPago;
import pe.alcanfores.pagos.modelo.Comprobante;
import pe.alcanfores.pagos.modelo.DatosFacturacion;
import pe.alcanfores.pagos.modelo.EstadoValidacion;
import pe.alcanfores.pagos.modelo.InstantaneaReserva;
import pe.alcanfores.pagos.modelo.MetodoPago;
import pe.alcanfores.pagos.modelo.Pago;
import pe.alcanfores.pagos.modelo.TipoComprobante;
import pe.alcanfores.pagos.modelo.TipoPago;
import pe.alcanfores.pagos.repositorio.DatosFacturacionRepositorio;
import pe.alcanfores.pagos.repositorio.PagoRepositorio;
import pe.alcanfores.pagos.servicio.documento.ConceptoReserva;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pagos de ejemplo coherentes con las reservas de la semilla de reservas-service (para que el panel, los comprobantes y
 * los reportes tengan datos). NO publica eventos: reservas ya tiene sus estados de pago.
 * <ul>
 *   <li>ADELANTO_PAGADO / PAGADO_TOTAL → pagos VALIDADOS con su boleta o factura (fechas del pasado).</li>
 *   <li>EN_REVISION → pago PENDIENTE con una imagen de constancia de ejemplo.</li>
 *   <li>OBSERVADO → pago RECHAZADO con observación (la misma que muestra la línea de tiempo de reservas).</li>
 *   <li>REEMBOLSADO (cancelada con devolución) → pago cobrado y luego REEMBOLSADO con su nota de crédito.</li>
 * </ul>
 */
@Service
public class SemillaPagos {

    private static final Logger log = LoggerFactory.getLogger(SemillaPagos.class);
    private static final int IMAGENES = 4;
    private static final String[] VALIDADORES = {"rhuaman", "mtorres"};
    /** Igual a {@code SemillaDatos.OBSERVACION_SEMILLA} de reservas-service: la reserva y el pago dicen lo mismo. */
    static final String OBSERVACION = "La imagen no permite leer el número de operación. Vuelve a subirla completa, por favor.";
    private static final String[][] EMPRESAS = {
            {"Inversiones Deportivas Santa Clara S.A.C. (ficticia)", "Av. Nicolás Ayllón 1234, Ate, Lima"},
            {"Academia de Fútbol Los Andes E.I.R.L. (ficticia)", "Jr. Los Cedros 456, Santa Clara, Ate, Lima"},
            {"Eventos Corporativos Huaycán S.R.L. (ficticia)", "Av. Andrés Avelino Cáceres 789, Ate, Lima"}};

    private final ReservasCliente reservas;
    private final PagoRepositorio pagos;
    private final DatosFacturacionRepositorio facturacion;
    private final ComprobanteServicio comprobantes;
    private final AlmacenComprobantes almacen;
    private final TransactionTemplate tx;

    public SemillaPagos(ReservasCliente reservas, PagoRepositorio pagos, DatosFacturacionRepositorio facturacion,
                        ComprobanteServicio comprobantes, AlmacenComprobantes almacen,
                        PlatformTransactionManager transacciones) {
        this.reservas = reservas;
        this.pagos = pagos;
        this.facturacion = facturacion;
        this.comprobantes = comprobantes;
        this.almacen = almacen;
        this.tx = new TransactionTemplate(transacciones);
    }

    public boolean hayPagos() {
        return pagos.count() > 0;
    }

    /**
     * @return pagos creados (0 si ya había pagos).
     * @throws pe.alcanfores.comun.error.NegocioExcepcion 503 si reservas-service aún no responde.
     */
    public int ejecutar() {
        List<ReservaSemilla> lista = reservas.paraSemilla();
        Integer creados = tx.execute(estado -> hayPagos() ? 0 : crear(lista));
        return creados == null ? 0 : creados;
    }

    int crear(List<ReservaSemilla> lista) {
        Random azar = new Random(2026);
        LocalDateTime ahora = Tiempo.ahora();
        List<Pago> cobrados = new ArrayList<>();
        Set<Pago> devueltos = new HashSet<>();
        int n = 0;
        List<ReservaSemilla> ordenadas = lista.stream()
                .filter(r -> r.codigoReserva() != null && r.cliente() != null && r.montoTotal() != null)
                .sorted(Comparator.comparing(ReservaSemilla::fechaRegistro, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (ReservaSemilla r : ordenadas) {
            LocalDateTime registro = r.fechaRegistro() != null ? r.fechaRegistro() : ahora.minusDays(1);
            InstantaneaReserva foto = foto(r);
            BigDecimal adelanto = r.montoAdelanto() != null ? r.montoAdelanto() : r.montoTotal();
            String estadoPago = r.estadoPago() == null ? "" : r.estadoPago();
            switch (estadoPago) {
                case "ADELANTO_PAGADO" -> {
                    BigDecimal monto = r.montoPagado() != null && r.montoPagado().signum() > 0 ? r.montoPagado() : adelanto;
                    TipoPago tipo = monto.compareTo(r.montoTotal()) >= 0 ? TipoPago.TOTAL : TipoPago.ADELANTO;
                    cobrados.add(cobro(r, foto, tipo, monto, registro, validacion(r, registro, azar, ahora), n, azar));
                }
                case "PAGADO_TOTAL" -> {
                    LocalDateTime validado = validacion(r, registro, azar, ahora);
                    if (n % 2 == 0 || adelanto.compareTo(r.montoTotal()) >= 0) {
                        cobrados.add(cobro(r, foto, TipoPago.TOTAL, r.montoTotal(), registro, validado, n, azar));
                    } else {
                        cobrados.add(cobro(r, foto, TipoPago.ADELANTO, adelanto, registro, validado, n, azar));
                        cobrados.add(saldoEnCaja(r, foto, r.montoTotal().subtract(adelanto), validado, ahora, n));
                    }
                }
                case "REEMBOLSADO" -> {
                    BigDecimal monto = r.montoDevolucion() != null && r.montoDevolucion().signum() > 0 ? r.montoDevolucion() : adelanto;
                    TipoPago tipo = monto.compareTo(r.montoTotal()) >= 0 ? TipoPago.TOTAL : TipoPago.ADELANTO;
                    Pago p = cobro(r, foto, tipo, monto, registro, validacion(r, registro, azar, ahora), n, azar);
                    cobrados.add(p);
                    devueltos.add(p);
                }
                case "EN_REVISION" -> pendienteOrechazado(r, foto, adelanto, registro, azar, n, false);
                case "OBSERVADO" -> pendienteOrechazado(r, foto, adelanto, registro, azar, n, true);
                default -> {
                    continue;
                }
            }
            n++;
        }
        // Correlativos en orden cronológico: la numeración de la semilla queda creciente con las fechas.
        cobrados.sort(Comparator.comparing(Pago::getFechaValidacion));
        for (Pago p : cobrados) {
            Comprobante c = comprobantes.emitirParaPago(p, p.getFechaValidacion(), false);
            if (devueltos.contains(p)) {
                LocalDateTime devuelto = antes(p.getFechaValidacion().plusHours(2), ahora);
                p.setEstado(EstadoValidacion.REEMBOLSADO);
                p.setObservacion("Reembolso simulado por cancelación de la reserva.");
                comprobantes.emitirNotaCredito(c, ComprobanteServicio.MOTIVO_DEVOLUCION,
                        "Devolución por cancelación de la reserva " + p.getCodigoReserva() + ".", devuelto, false);
            }
        }
        int creados = (int) pagos.count();
        log.info("Semilla de pagos: {} pago(s) y {} comprobante(s) de ejemplo a partir de {} reserva(s)", creados,
                cobrados.size(), ordenadas.size());
        return creados;
    }

    private Pago cobro(ReservaSemilla r, InstantaneaReserva foto, TipoPago tipo, BigDecimal monto,
                       LocalDateTime registro, LocalDateTime validado, int n, Random azar) {
        String previsto = r.metodoPagoPrevisto() == null ? "" : r.metodoPagoPrevisto();
        Pago p;
        switch (previsto) {
            case "YAPE", "PLIN" -> {
                LocalDateTime envio = antes(registro.plusMinutes(3 + azar.nextInt(4)), validado);
                p = Pago.nuevo(r.codigoReserva(), foto, MetodoPago.valueOf(previsto), CanalPago.COMPROBANTE, tipo, monto,
                        EstadoValidacion.PENDIENTE, envio);
                p.setCodigoOperacion(digitos(azar, 8));
                p.setArchivoComprobante(imagenDeEjemplo(n));
                p.validar(validado, null, VALIDADORES[n % 2]);
            }
            case "TARJETA" -> {
                p = Pago.nuevo(r.codigoReserva(), foto, MetodoPago.TARJETA, CanalPago.PASARELA, tipo, monto,
                        EstadoValidacion.PENDIENTE, antes(registro.plusMinutes(2), validado));
                p.setReferenciaPasarela("ALP-" + digitos(azar, 10));
                p.setMarcaTarjeta(n % 2 == 0 ? "Visa" : "Mastercard");
                p.setUltimos4(n % 2 == 0 ? "1111" : "4444");
                p.setCodigoAutorizacion(digitos(azar, 6));
                p.validar(p.getFechaEnvio(), null, "Pasarela");
            }
            case "MERCADO_PAGO" -> {
                p = Pago.nuevo(r.codigoReserva(), foto, MetodoPago.MERCADO_PAGO, CanalPago.PASARELA, tipo, monto,
                        EstadoValidacion.PENDIENTE, antes(registro.plusMinutes(4), validado));
                p.setReferenciaPasarela("ALP-" + digitos(azar, 10));
                p.setCodigoOperacion(digitos(azar, 11));
                p.validar(p.getFechaEnvio(), null, "Mercado Pago");
            }
            default -> {
                MetodoPago metodo = "TARJETA_POS".equals(previsto) ? MetodoPago.TARJETA_POS : MetodoPago.EFECTIVO;
                p = Pago.nuevo(r.codigoReserva(), foto, metodo, CanalPago.CAJA, tipo, monto, EstadoValidacion.PENDIENTE,
                        registro);
                p.validar(registro, null, VALIDADORES[1]);
            }
        }
        pagos.save(p);
        guardarFacturacion(p, n);
        return p;
    }

    private Pago saldoEnCaja(ReservaSemilla r, InstantaneaReserva foto, BigDecimal saldo, LocalDateTime adelantoValidado,
                             LocalDateTime ahora, int n) {
        LocalDateTime momento = r.fecha() != null && r.horaInicio() != null
                ? LocalDateTime.of(r.fecha(), r.horaInicio()).minusMinutes(10) : adelantoValidado.plusHours(1);
        momento = antes(momento.isBefore(adelantoValidado) ? adelantoValidado.plusMinutes(30) : momento, ahora);
        Pago p = Pago.nuevo(r.codigoReserva(), foto, MetodoPago.EFECTIVO, CanalPago.CAJA, TipoPago.SALDO, saldo,
                EstadoValidacion.PENDIENTE, momento);
        p.validar(momento, null, VALIDADORES[1]);
        pagos.save(p);
        guardarFacturacion(p, n);
        return p;
    }

    private void pendienteOrechazado(ReservaSemilla r, InstantaneaReserva foto, BigDecimal adelanto,
                                     LocalDateTime registro, Random azar, int n, boolean rechazado) {
        MetodoPago metodo = "PLIN".equals(r.metodoPagoPrevisto()) ? MetodoPago.PLIN : MetodoPago.YAPE;
        LocalDateTime envio = antes(registro.plusMinutes(4 + azar.nextInt(6)), Tiempo.ahora());
        Pago p = Pago.nuevo(r.codigoReserva(), foto, metodo, CanalPago.COMPROBANTE, TipoPago.ADELANTO, adelanto,
                EstadoValidacion.PENDIENTE, envio);
        p.setCodigoOperacion(digitos(azar, 8));
        p.setArchivoComprobante(imagenDeEjemplo(n));
        if (rechazado) {
            p.setEstado(EstadoValidacion.RECHAZADO);
            p.setObservacion(OBSERVACION);
            p.setFechaValidacion(antes(envio.plusMinutes(12), Tiempo.ahora()));
            p.setValidadoPor(VALIDADORES[n % 2]);
        }
        pagos.save(p);
        guardarFacturacion(p, n);
    }

    /** Una de cada cuatro reservas pide factura (RUC de prueba 2000000XXXD, válido por módulo 11). */
    private void guardarFacturacion(Pago p, int n) {
        DatosFacturacion df = new DatosFacturacion();
        df.setCodigoReserva(p.getCodigoReserva());
        df.setIdPago(p.getId());
        if (n % 4 == 3) {
            String[] empresa = EMPRESAS[(n / 4) % EMPRESAS.length];
            df.setTipo(TipoComprobante.FACTURA);
            df.setTipoDoc(DatosFacturacion.DOC_RUC);
            df.setNumDoc(rucDePrueba((n / 4) % EMPRESAS.length));
            df.setNombre(empresa[0]);
            df.setDireccion(empresa[1]);
        } else {
            df.setTipo(TipoComprobante.BOLETA);
            df.setTipoDoc(DatosFacturacion.DOC_DNI);
            df.setNumDoc(p.getReserva().getClienteDni());
            df.setNombre(p.getReserva().nombreCompleto());
        }
        facturacion.save(df);
    }

    static String rucDePrueba(int indice) {
        String diez = "2000000" + String.format("%03d", 101 + indice);
        return diez + Documentos.digitoVerificadorRuc(diez);
    }

    private static InstantaneaReserva foto(ReservaSemilla r) {
        return new InstantaneaReserva(r.cliente().dni(), r.cliente().nombres(), r.cliente().apellidos(),
                r.cliente().telefono(), r.cliente().correo(),
                ConceptoReserva.concepto(r.nombreCancha(), r.deporte(), r.fecha(), r.horaInicio(), r.horaFin(),
                        r.codigoReserva()),
                ConceptoReserva.porcentaje(r.montoAdelanto(), r.montoTotal()));
    }

    private static LocalDateTime validacion(ReservaSemilla r, LocalDateTime registro, Random azar, LocalDateTime ahora) {
        LocalDateTime v = r.confirmadaEn() != null ? r.confirmadaEn() : registro.plusMinutes(8 + azar.nextInt(33));
        return antes(v, ahora);
    }

    private static LocalDateTime antes(LocalDateTime momento, LocalDateTime limite) {
        return momento.isAfter(limite) ? limite : momento;
    }

    private static String digitos(Random azar, int cantidad) {
        StringBuilder sb = new StringBuilder();
        sb.append(1 + azar.nextInt(9));
        while (sb.length() < cantidad) {
            sb.append(azar.nextInt(10));
        }
        return sb.toString();
    }

    private String imagenDeEjemplo(int n) {
        String recurso = "semilla/comprobantes/ejemplo-" + (n % IMAGENES + 1) + ".png";
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            return almacen.guardar(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la imagen de ejemplo " + recurso, e);
        }
    }
}
