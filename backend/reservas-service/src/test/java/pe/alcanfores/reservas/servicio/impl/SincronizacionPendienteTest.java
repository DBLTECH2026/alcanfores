package pe.alcanfores.reservas.servicio.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.eventos.CalendarioActualizadoEvento;
import pe.alcanfores.reservas.cliente.ParametrosComplejo;
import pe.alcanfores.reservas.evento.IdempotenciaEventos;
import pe.alcanfores.reservas.repositorio.ReservaTurnoRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;
import pe.alcanfores.reservas.tarea.TareasProgramadas;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Si catálogo arrancó después que reservas, la primera lectura falla y la regeneración queda en «solo crear». Un evento de
 * catálogo (o el reintento cada 30 s) debe leerlo otra vez antes de regenerar.
 */
@ExtendWith(MockitoExtension.class)
class SincronizacionPendienteTest {

    @Mock
    private TurnoRepositorio turnos;
    @Mock
    private ReservaTurnoRepositorio reservaTurnos;
    @Mock
    private ComplejoServicio complejo;
    @Mock
    private PlanificadorTurnos planificador;
    @Mock
    private IdempotenciaEventos idempotencia;
    @Mock
    private Auditor auditor;
    @Mock
    private TransactionTemplate transacciones;
    @InjectMocks
    private TurnoServicioImpl servicio;

    private CalendarioActualizadoEvento eventoLejano() {
        // Fuera del horizonte de turnos: solo se actualiza el calendario en memoria.
        return new CalendarioActualizadoEvento(UUID.randomUUID(), LocalDateTime.now(), LocalDate.now().plusYears(2),
                CalendarioActualizadoEvento.FERIADO, "Feriado lejano", null, false);
    }

    @Test
    void unEventoConCatalogoSinLeerLoLeeAntesDeRegenerar() {
        when(idempotencia.primeraVez(any(), any())).thenReturn(true);
        when(complejo.catalogoLeido()).thenReturn(false);
        when(complejo.refrescar()).thenReturn(List.of());
        when(complejo.refrescarCalendario()).thenReturn(List.of());
        when(complejo.parametros()).thenReturn(ParametrosComplejo.porDefecto());

        servicio.alActualizarCalendario(eventoLejano());

        verify(complejo).refrescar();
        verify(complejo).refrescarCalendario();
    }

    @Test
    void conCatalogoYaLeidoNoSeVuelveALeer() {
        when(idempotencia.primeraVez(any(), any())).thenReturn(true);
        when(complejo.catalogoLeido()).thenReturn(true);
        when(complejo.parametros()).thenReturn(ParametrosComplejo.porDefecto());

        servicio.alActualizarCalendario(eventoLejano());
        servicio.sincronizarSiFalta();

        verify(complejo, never()).refrescar();
        verify(complejo, never()).refrescarCalendario();
    }

    @Test
    void laTareaReintentaCadaTreintaSegundos() {
        TurnoServicio turnoServicio = mock(TurnoServicio.class);
        new TareasProgramadas(turnoServicio, mock(ExpiracionServicio.class)).reintentarCatalogo();
        verify(turnoServicio).sincronizarSiFalta();
    }
}
