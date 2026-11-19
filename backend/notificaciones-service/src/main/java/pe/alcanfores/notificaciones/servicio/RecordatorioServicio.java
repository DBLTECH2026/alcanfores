package pe.alcanfores.notificaciones.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.notificaciones.dto.EjecucionRecordatoriosRespuesta;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;

import java.math.BigDecimal;

/** Recordatorio «tu partido es en 2 horas»: programar, cancelar, enviar los vencidos (cron) y listar. */
public interface RecordatorioServicio {

    /** Crea (o reprograma) el recordatorio con {@code enviar_en = inicio − horasAntes}. */
    void programar(DatosReserva reserva, BigDecimal saldoPendiente, int horasAntes);

    /** Pasa a CANCELADO si aún estaba PROGRAMADO. */
    void cancelar(String codigoReserva, String motivo);

    /** Envía los PROGRAMADOS cuya hora llegó; OMITIDO si faltan menos de 30 min para el inicio. */
    EjecucionRecordatoriosRespuesta ejecutarPendientes();

    /** Lo mismo, pedido desde el panel (para la demo): queda en la bitácora. */
    EjecucionRecordatoriosRespuesta ejecutarAhora();

    PaginaRespuesta<RecordatorioRespuesta> listar(EstadoRecordatorio estado, Pageable pagina);
}
