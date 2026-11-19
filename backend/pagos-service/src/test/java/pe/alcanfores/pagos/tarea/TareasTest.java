package pe.alcanfores.pagos.tarea;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.servicio.IntentoPagoServicio;
import pe.alcanfores.pagos.servicio.SemillaPagos;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareasTest {

    static final NegocioExcepcion CAIDO = NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "reservas no responde");

    private Runnable programar(SemillaTarea tarea, TaskScheduler programador, ScheduledFuture<?> futuro) {
        doReturn(futuro).when(programador).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        tarea.alIniciar();
        ArgumentCaptor<Runnable> trabajo = ArgumentCaptor.forClass(Runnable.class);
        verify(programador).scheduleWithFixedDelay(trabajo.capture(), eq(SemillaTarea.ESPERA));
        return trabajo.getValue();
    }

    @Test
    void semillaReintentaHastaQueReservasRespondeYLuegoSeDetiene() {
        SemillaPagos semilla = mock(SemillaPagos.class);
        TaskScheduler programador = mock(TaskScheduler.class);
        ScheduledFuture<?> futuro = mock(ScheduledFuture.class);
        when(semilla.ejecutar()).thenThrow(CAIDO).thenThrow(CAIDO).thenReturn(12);

        Runnable trabajo = programar(new SemillaTarea(semilla, programador, true), programador, futuro);
        trabajo.run();
        trabajo.run();
        verify(futuro, never()).cancel(false);
        trabajo.run();
        verify(futuro).cancel(false);
        trabajo.run();
        verify(semilla, times(3)).ejecutar();
    }

    @Test
    void semillaSeAbandonaTrasVeinteIntentos() {
        SemillaPagos semilla = mock(SemillaPagos.class);
        TaskScheduler programador = mock(TaskScheduler.class);
        ScheduledFuture<?> futuro = mock(ScheduledFuture.class);
        when(semilla.ejecutar()).thenThrow(CAIDO);

        Runnable trabajo = programar(new SemillaTarea(semilla, programador, true), programador, futuro);
        for (int i = 0; i < SemillaTarea.MAX_INTENTOS + 3; i++) {
            trabajo.run();
        }
        verify(semilla, times(SemillaTarea.MAX_INTENTOS)).ejecutar();
        verify(futuro, times(4)).cancel(false);
    }

    @Test
    void semillaDeshabilitadaOConPagosNoSeProgramada() {
        SemillaPagos semilla = mock(SemillaPagos.class);
        TaskScheduler programador = mock(TaskScheduler.class);
        new SemillaTarea(semilla, programador, false).alIniciar();
        when(semilla.hayPagos()).thenReturn(true);
        new SemillaTarea(semilla, programador, true).alIniciar();
        verify(programador, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void expiracionDeIntentos() {
        IntentoPagoServicio intentos = mock(IntentoPagoServicio.class);
        when(intentos.expirarVencidos()).thenReturn(0, 3);
        ExpirarIntentosTarea tarea = new ExpirarIntentosTarea(intentos);
        tarea.expirar();
        tarea.expirar();
        verify(intentos, times(2)).expirarVencidos();
    }
}
