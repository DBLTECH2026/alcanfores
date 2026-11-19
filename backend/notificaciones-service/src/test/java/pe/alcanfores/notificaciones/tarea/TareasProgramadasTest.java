package pe.alcanfores.notificaciones.tarea;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import pe.alcanfores.notificaciones.servicio.NotificacionServicio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareasProgramadasTest {

    private final RecordatorioServicio recordatorios = mock(RecordatorioServicio.class);
    private final NotificacionServicio notificaciones = mock(NotificacionServicio.class);
    private final TareasProgramadas tareas = new TareasProgramadas(recordatorios, notificaciones);

    @Test
    @DisplayName("Cron cada minuto (recordatorios) y cada 5 minutos (reintentos) en hora de Lima")
    void cron() throws NoSuchMethodException {
        Scheduled rec = TareasProgramadas.class.getMethod("enviarRecordatorios").getAnnotation(Scheduled.class);
        Scheduled rei = TareasProgramadas.class.getMethod("reintentarFallidas").getAnnotation(Scheduled.class);
        assertThat(rec.cron()).isEqualTo("0 * * * * *");
        assertThat(rec.zone()).isEqualTo("America/Lima");
        assertThat(rei.cron()).isEqualTo("30 */5 * * * *");
        assertThat(rei.zone()).isEqualTo("America/Lima");
    }

    @Test
    @DisplayName("Delegan en los servicios y una falla no tumba el planificador")
    void delegan() {
        tareas.enviarRecordatorios();
        tareas.reintentarFallidas();
        verify(recordatorios).ejecutarPendientes();
        verify(notificaciones).reintentarFallidas();

        when(recordatorios.ejecutarPendientes()).thenThrow(new IllegalStateException("BD caída"));
        when(notificaciones.reintentarFallidas()).thenThrow(new IllegalStateException("BD caída"));
        assertThatCode(tareas::enviarRecordatorios).doesNotThrowAnyException();
        assertThatCode(tareas::reintentarFallidas).doesNotThrowAnyException();
    }
}
