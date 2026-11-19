package pe.alcanfores.notificaciones.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.AvisoServicio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatosDemoTest {

    @Test
    @DisplayName("Con la bandeja vacía pasa los eventos de ejemplo por el flujo real")
    void cargaSiEstaVacia() {
        AvisoServicio avisos = mock(AvisoServicio.class);
        NotificacionRepositorio repo = mock(NotificacionRepositorio.class);
        when(repo.count()).thenReturn(0L, 12L);

        new DatosDemo(avisos, repo).run(null);

        verify(avisos, times(2)).alRegistrarReserva(any());
        verify(avisos).alConfirmarReserva(any());
        verify(avisos).alEmitirComprobante(any());
        verify(avisos).alRegistrarAsistencia(any());
        verify(avisos).alCancelarReserva(any());
    }

    @Test
    @DisplayName("Si ya hay avisos no hace nada")
    void noDuplica() {
        AvisoServicio avisos = mock(AvisoServicio.class);
        NotificacionRepositorio repo = mock(NotificacionRepositorio.class);
        when(repo.count()).thenReturn(5L);

        new DatosDemo(avisos, repo).run(null);

        verifyNoInteractions(avisos);
    }
}
