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
    public void programar(DatosReserva reserva, BigDecimal saldoPendiente, int horasAntes) {
        LocalDateTime inicio = LocalDateTime.of(reserva.fecha(), reserva.horaInicio());
        LocalDateTime enviarEn = inicio.minusHours(horasAntes);
        DatosRecordatorio datos = new DatosRecordatorio(reserva, saldoPendiente);
        recordatorios.findByCodigoReserva(reserva.codigoReserva()).ifPresentOrElse(r -> {
            if (r.getEstado() != EstadoRecordatorio.ENVIADO) {
                r.reprogramar(enviarEn, inicio, datos);
            }
        }, () -> recordatorios.save(new Recordatorio(reserva.codigoReserva(), enviarEn, inicio, datos)));
        log.info("Recordatorio de {} programado para {}", reserva.codigoReserva(), enviarEn);
    }

    @Override
    @Transactional
    public void cancelar(String codigoReserva, String motivo) {
        recordatorios.findByCodigoReserva(codigoReserva).filter(Recordatorio::programado).ifPresent(r -> {
            r.cancelar(motivo);
            log.info("Recordatorio de {} cancelado: {}", codigoReserva, motivo);
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
    public PaginaRespuesta<RecordatorioRespuesta> listar(EstadoRecordatorio estado, Pageable pagina) {
        Page<Recordatorio> p = estado == null ? recordatorios.findAll(pagina) : recordatorios.findByEstado(estado, pagina);
        return PaginaRespuesta.de(p, RecordatorioMapeador::aRespuesta);
    }

    /** «de hoy», «de mañana» o «del viernes 20 de noviembre de 2026» (si se configuró con muchas horas de anticipación). */
    static String cuando(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) {
            return "de hoy";
        }
        return fecha.equals(hoy.plusDays(1)) ? "de mañana" : "del " + Tiempo.fechaLarga(fecha);
    }

    private EstadoRecordatorio procesar(Recordatorio r, LocalDateTime ahora) {
        long segundosRestantes = Duration.between(ahora, r.getInicioReserva()).toSeconds();
        if (segundosRestantes < propiedades.getRecordatorio().getMinutosMinimos() * 60L) {
            r.omitir(segundosRestantes <= 0 ? "El partido ya había empezado al revisar el recordatorio."
                    : "Faltaban menos de " + propiedades.getRecordatorio().getMinutosMinimos() + " minutos para el inicio.");
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
        long minutosRedondeados = Math.round(segundosRestantes / 300.0) * 5;
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
