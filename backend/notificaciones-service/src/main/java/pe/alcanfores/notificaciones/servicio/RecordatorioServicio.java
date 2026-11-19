package pe.alcanfores.notificaciones.servicio;

import org.springframework.data.domain.Pageable;
import pe.alcanfores.comun.eventos.DatosReserva;
import pe.alcanfores.comun.util.PaginaRespuesta;
import pe.alcanfores.notificaciones.dto.EjecucionRecordatoriosRespuesta;
import pe.alcanfores.notificaciones.dto.RecordatorioRespuesta;
import pe.alcanfores.notificaciones.modelo.EstadoRecordatorio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Recordatorio «tu partido es en 2 horas»: programar, cancelar, enviar los vencidos (cron) y listar. */
public interface RecordatorioServicio {

    /**
     * Crea (o reprograma) un recordatorio por cada valor de la lista, con {@code enviar_en = inicio − minutos}.
     * {@code confirmadaEn} es cuándo ocurrió la confirmación: si los recordatorios ya tienen datos de ese momento o de
     * después (un cambio de horario que llegó antes que la confirmación), no se tocan; así nunca vuelven al horario viejo.
     */
    void programar(DatosReserva reserva, BigDecimal saldoPendiente, List<Integer> minutosAntes, LocalDateTime confirmadaEn);

    /**
     * La reserva confirmada cambió de horario: cada tiempo de la lista vigente vuelve a PROGRAMADO para el nuevo inicio
     * (también los ya enviados u omitidos del horario anterior: es otro partido) y se cancelan los que ya no están.
     * {@code cambiadaEn} es cuándo ocurrió el cambio: un cambio más viejo que los datos guardados se ignora.
     */
    void moverAlNuevoInicio(DatosReserva reserva, BigDecimal saldoPendiente, List<Integer> minutosAntes,
                            LocalDateTime cambiadaEn);

    /** Pasa a CANCELADO todos los recordatorios de la reserva que aún estaban PROGRAMADOS. */
    void cancelar(String codigoReserva, String motivo);

    /** Envía los PROGRAMADOS cuya hora llegó; OMITIDO si su hora pasó hace más de 10 min o el partido ya empezó. */
    EjecucionRecordatoriosRespuesta ejecutarPendientes();

    /** Lo mismo, pedido desde el panel (para la demo): queda en la bitácora. */
    EjecucionRecordatoriosRespuesta ejecutarAhora();

    PaginaRespuesta<RecordatorioRespuesta> listar(EstadoRecordatorio estado, String codigoReserva, Pageable pagina);
}
