package pe.alcanfores.notificaciones.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.ReservaInterna;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.dto.EjecucionRecordatoriosRespuesta;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.mapeador.RecordatorioMapeador;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.DatosRecordatorio;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.modelo.Recordatorio;
import pe.alcanfores.notificaciones.plantilla.ModeloMensajes;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.repositorio.RecordatorioRepositorio;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RecordatorioServicioImpl implements RecordatorioServicio {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioServicioImpl.class);

    private final RecordatorioRepositorio recordatorios;
    private final NotificacionRepositorio notificaciones;
    private final ReservasCliente reservas;
    private final ComplejoServicio complejo;
    private final ModeloMensajes modelos;
    private final FabricaAvisos fabrica;
    private final EnvioServicio envio;
    private final NotificacionesPropiedades propiedades;
    private final Auditor auditor;

    public RecordatorioServicioImpl(RecordatorioRepositorio recordatorios, NotificacionRepositorio notificaciones,
                                    ReservasCliente reservas, ComplejoServicio complejo, ModeloMensajes modelos,
                                    FabricaAvisos fabrica, EnvioServicio envio, NotificacionesPropiedades propiedades,
                                    Auditor auditor) {
        this.recordatorios = recordatorios;
        this.notificaciones = notificaciones;
        this.reservas = reservas;
        this.complejo = complejo;
        this.modelos = modelos;
        this.fabrica = fabrica;
        this.envio = envio;
        this.propiedades = propiedades;
        this.auditor = auditor;
    }

    @Override
    @Transactional
    public void programar(DatosReserva reserva, BigDecimal saldoPendiente, List<Integer> minutosAntes,
                          LocalDateTime confirmadaEn) {
        // Un cambio de horario ve la reserva ya confirmada: si sus datos son del mismo segundo o posteriores, mandan.
        if (hayDatosDesde(reserva.codigoReserva(), confirmadaEn, true)) {
            log.info("Confirmación de {} llegó después de un cambio de horario: los recordatorios se quedan como están",
                    reserva.codigoReserva());
            return;
        }
        LocalDateTime inicio = LocalDateTime.of(reserva.fecha(), reserva.horaInicio());
        DatosRecordatorio datos = new DatosRecordatorio(reserva, saldoPendiente);
        for (Integer minutos : minutosAntes) {
            recordatorios.findByCodigoReservaAndMinutosAntes(reserva.codigoReserva(), minutos).ifPresentOrElse(r -> {
                if (r.getEstado() != EstadoRecordatorio.ENVIADO) {
                    r.reprogramar(inicio, datos, confirmadaEn);
                }
            }, () -> recordatorios.save(new Recordatorio(reserva.codigoReserva(), minutos, inicio, datos, confirmadaEn)));
        }
        // Si la reserva se vuelve a confirmar con otra lista, los tiempos que ya no están se cancelan.
        recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(reserva.codigoReserva()).stream()
                .filter(r -> r.programado() && !minutosAntes.contains(r.getMinutosAntes()))
                .forEach(r -> r.cancelar("Ya no está en la lista de recordatorios del complejo."));
        log.info("Recordatorios de {} programados {} minutos antes de {}", reserva.codigoReserva(), minutosAntes, inicio);
    }

    @Override
    @Transactional
    public void moverAlNuevoInicio(DatosReserva reserva, BigDecimal saldoPendiente, List<Integer> minutosAntes,
                                   LocalDateTime cambiadaEn) {
        if (hayDatosDesde(reserva.codigoReserva(), cambiadaEn, false)) {
            log.info("Cambio de horario de {} más viejo que los recordatorios guardados: se ignora", reserva.codigoReserva());
            return;
        }
        LocalDateTime inicio = LocalDateTime.of(reserva.fecha(), reserva.horaInicio());
        DatosRecordatorio datos = new DatosRecordatorio(reserva, saldoPendiente);
        for (Integer minutos : minutosAntes) {
            recordatorios.findByCodigoReservaAndMinutosAntes(reserva.codigoReserva(), minutos).ifPresentOrElse(
                    r -> r.reprogramar(inicio, datos, cambiadaEn),
                    () -> recordatorios.save(new Recordatorio(reserva.codigoReserva(), minutos, inicio, datos, cambiadaEn)));
        }
        recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(reserva.codigoReserva()).stream()
                .filter(r -> r.programado() && !minutosAntes.contains(r.getMinutosAntes()))
                .forEach(r -> r.cancelar("Ya no está en la lista de recordatorios del complejo."));
        log.info("Recordatorios de {} movidos al nuevo horario: {} minutos antes de {}", reserva.codigoReserva(),
                minutosAntes, inicio);
    }

    @Override
    @Transactional
    public void cancelar(String codigoReserva, String motivo) {
        recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(codigoReserva).stream()
                .filter(Recordatorio::programado)
                .forEach(r -> {
                    r.cancelar(motivo);
                    log.info("Recordatorio de {} ({} min antes) cancelado: {}", codigoReserva, r.getMinutosAntes(), motivo);
                });
    }

    @Override
    public EjecucionRecordatoriosRespuesta ejecutarPendientes() {
        LocalDateTime ahora = Tiempo.ahora();
        List<Recordatorio> vencidos = recordatorios
                .findByEstadoAndEnviarEnLessThanEqualOrderByEnviarEnAsc(EstadoRecordatorio.PROGRAMADO, ahora);
        int enviados = 0;
        int omitidos = 0;
        int cancelados = 0;
        for (Recordatorio r : vencidos) {
            switch (procesar(r, ahora)) {
                case ENVIADO -> enviados++;
                case OMITIDO -> omitidos++;
                case CANCELADO -> cancelados++;
                default -> log.debug("Recordatorio {} lo tomó otra instancia", r.getCodigoReserva());
            }
        }
        if (!vencidos.isEmpty()) {
            log.info("Recordatorios: {} revisados, {} enviados, {} omitidos, {} cancelados", vencidos.size(), enviados,
                    omitidos, cancelados);
        }
        return new EjecucionRecordatoriosRespuesta(vencidos.size(), enviados, omitidos, cancelados);
    }

    @Override
    public EjecucionRecordatoriosRespuesta ejecutarAhora() {
        EjecucionRecordatoriosRespuesta r = ejecutarPendientes();
        auditor.registrar("EJECUTAR_RECORDATORIOS", "recordatorio", null, "Ejecución manual: " + r.revisados()
                + " revisados, " + r.enviados() + " enviados, " + r.omitidos() + " omitidos, " + r.cancelados() + " cancelados");
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaRespuesta<RecordatorioRespuesta> listar(EstadoRecordatorio estado, String codigoReserva, Pageable pagina) {
        String codigo = codigoReserva == null || codigoReserva.isBlank() ? null : codigoReserva.trim().toUpperCase(Locale.ROOT);
        Page<Recordatorio> p;
        if (codigo == null) {
            p = estado == null ? recordatorios.findAll(pagina) : recordatorios.findByEstado(estado, pagina);
        } else {
            p = estado == null ? recordatorios.findByCodigoReserva(codigo, pagina)
                    : recordatorios.findByCodigoReservaAndEstado(codigo, estado, pagina);
        }
        return PaginaRespuesta.de(p, RecordatorioMapeador::aRespuesta);
    }

    /**
     * ¿Algún recordatorio de la reserva tiene datos de un evento posterior a {@code momento} (o del mismo segundo, si
     * {@code incluirMismoMomento})? Los eventos de la misma reserva llegan por colas distintas y pueden cruzarse.
     */
    private boolean hayDatosDesde(String codigoReserva, LocalDateTime momento, boolean incluirMismoMomento) {
        if (momento == null) {
            return false;
        }
        return recordatorios.findByCodigoReservaOrderByMinutosAntesDesc(codigoReserva).stream()
                .map(Recordatorio::getDatosAl)
                .anyMatch(d -> d != null && (d.isAfter(momento) || (incluirMismoMomento && d.equals(momento))));
    }

    /** «de hoy», «de mañana» o «del viernes 20 de noviembre de 2026» (si se configuró con muchas horas de anticipación). */
    static String cuando(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) {
            return "de hoy";
        }
        return fecha.equals(hoy.plusDays(1)) ? "de mañana" : "del " + Tiempo.fechaLarga(fecha);
    }

    /**
     * Si reservas dice que la reserva empieza a otra hora (cambió de horario y el aviso de ese cambio aún no llega, o
     * llegó antes que la confirmación), el recordatorio se mueve al inicio vigente en vez de salir con la hora vieja.
     */
    private boolean cambioDeHorario(Recordatorio r, ReservaInterna actual) {
        DatosReserva vigente = actual.datos();
        if (vigente == null || vigente.fecha() == null || vigente.horaInicio() == null) {
            return false;
        }
        LocalDateTime inicio = LocalDateTime.of(vigente.fecha(), vigente.horaInicio());
        LocalDateTime anterior = r.getInicioReserva();
        if (inicio.equals(anterior)) {
            return false;
        }
        r.reprogramar(inicio, new DatosRecordatorio(vigente, actual.saldo() != null ? actual.saldo()
                : r.getDatos().saldoPendiente()), Tiempo.ahora());
        recordatorios.save(r);
        log.info("Recordatorio de {} movido de {} a {}: la reserva cambió de horario", r.getCodigoReserva(), anterior, inicio);
        return true;
    }

    private EstadoRecordatorio procesar(Recordatorio r, LocalDateTime ahora) {
        long segundosRestantes = Duration.between(ahora, r.getInicioReserva()).toSeconds();
        int tolerancia = propiedades.getRecordatorio().getToleranciaMinutos();
        if (segundosRestantes <= 0 || ahora.isAfter(r.getEnviarEn().plusMinutes(tolerancia))) {
            r.omitir(segundosRestantes <= 0 ? "El partido ya había empezado al revisar el recordatorio."
                    : "Su hora de envío pasó hace más de " + tolerancia + " minutos (la reserva se confirmó tarde).");
            recordatorios.save(r);
            return EstadoRecordatorio.OMITIDO;
        }
        BigDecimal saldo = r.getDatos().saldoPendiente();
        try {
            ReservaInterna actual = reservas.consultar(r.getCodigoReserva());
            if (actual.cancelada()) {
                r.cancelar("La reserva ya estaba cancelada al enviar el recordatorio.");
                recordatorios.save(r);
                return EstadoRecordatorio.CANCELADO;
            }
            if (actual.saldo() != null) {
                saldo = actual.saldo();
            }
            if (cambioDeHorario(r, actual)) {
                return EstadoRecordatorio.PROGRAMADO;
            }
        } catch (NegocioExcepcion e) {
            log.info("Recordatorio {}: reservas no respondió ({}); se usan los datos guardados", r.getCodigoReserva(),
                    e.getMessage());
        }
        if (recordatorios.tomarParaEnvio(r.getId(), ahora) == 0) {
            return EstadoRecordatorio.PROGRAMADO;
        }
        DatosReserva reserva = r.getDatos().reserva();
        DatosComplejo c = complejo.datos();
        Map<String, Object> modelo = modelos.reserva(reserva, c);
        modelos.fijarSaldo(modelo, saldo);
        long minutosRedondeados = Math.max(5, Math.round(segundosRestantes / 300.0) * 5);
        String falta = ModeloMensajes.duracionTexto(minutosRedondeados);
        modelo.put("falta", falta);
        modelo.put("cuando", cuando(reserva.fecha(), ahora.toLocalDate()));
        String asunto = "Tu partido es en " + falta + " · " + reserva.nombreCancha() + " · " + reserva.codigoReserva();
        List<Notificacion> avisos = fabrica.crear(TipoAviso.RECORDATORIO, asunto, modelo, reserva.codigoReserva(),
                reserva.cliente(), List.<Adjunto>of());
        envio.enviarTodas(notificaciones.saveAll(avisos));
        return EstadoRecordatorio.ENVIADO;
    }
}
