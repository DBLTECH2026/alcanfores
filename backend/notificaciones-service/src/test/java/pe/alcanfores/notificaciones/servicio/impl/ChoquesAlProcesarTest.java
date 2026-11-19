package pe.alcanfores.notificaciones.servicio.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.alcanfores.comun.eventos.AsistenciaRegistradaEvento;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.plantilla.ModeloMensajes;
import pe.alcanfores.notificaciones.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;
import pe.alcanfores.notificaciones.servicio.RecordatorioServicio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un choque de unicidad al guardar solo se da por «evento duplicado» si ese evento quedó registrado (lo procesó otra
 * copia). Si fue otro choque (dos eventos de la misma reserva creando a la vez su recordatorio), se relanza para que
 * RabbitMQ lo reintente y el aviso no se pierda en silencio.
 */
class ChoquesAlProcesarTest {

    private final EventoProcesadoRepositorio eventos = mock(EventoProcesadoRepositorio.class);
    private final EnvioServicio envio = mock(EnvioServicio.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private AvisoServicioImpl avisos;

    @BeforeEach
    void preparar() {
        avisos = new AvisoServicioImpl(eventos, mock(NotificacionRepositorio.class), mock(RecordatorioServicio.class), envio,
                mock(FabricaAvisos.class), mock(ModeloMensajes.class), mock(ComplejoServicio.class),
                mock(ReservasCliente.class), tx);
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));
    }

    @Test
    void siOtraCopiaYaProcesoElEventoSeIgnora() {
        when(eventos.existsById(anyString())).thenReturn(false, true);

        assertThatCode(() -> avisos.alRegistrarAsistencia(Datos.asistencia("NO_ASISTIO"))).doesNotThrowAnyException();
        verify(envio, never()).enviarTodas(any());
    }

    @Test
    void siElChoqueEsDeOtraCosaSeRelanzaParaQueRabbitReintente() {
        when(eventos.existsById(anyString())).thenReturn(false, false);
        AsistenciaRegistradaEvento e = Datos.asistencia("NO_ASISTIO");

        assertThatThrownBy(() -> avisos.alRegistrarAsistencia(e)).isInstanceOf(DataIntegrityViolationException.class);
        verify(envio, never()).enviarTodas(any());
    }
}
