package pe.alcanfores.notificaciones.servicio.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;
import pe.alcanfores.notificaciones.cliente.EvolutionExcepcion;
import pe.alcanfores.notificaciones.dto.ConexionWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.EstadoWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.MensajeRespuesta;
import pe.alcanfores.notificaciones.mapeador.NotificacionMapeador;
import pe.alcanfores.notificaciones.plantilla.Tarjetas;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WhatsApp configurado: estado, QR, desconexión y errores de Evolution → 503. */
class WhatsappServicioImplTest {

    private EvolutionCliente evolution;
    private Auditor auditor;
    private WhatsappServicioImpl servicio;

    @BeforeEach
    void preparar() {
        evolution = mock(EvolutionCliente.class);
        when(evolution.configurado()).thenReturn(true);
        when(evolution.instancia()).thenReturn("alcanfores");
        auditor = mock(Auditor.class);
        servicio = new WhatsappServicioImpl(evolution, mock(NotificacionRepositorio.class), mock(EnvioServicio.class),
                new NotificacionMapeador(new Tarjetas()), auditor);
    }

    @Test
    @DisplayName("Estado conectado con el número vinculado")
    void estado() {
        when(evolution.estado()).thenReturn(new EvolutionCliente.EstadoConexion("open", "51914995187"));

        assertThat(servicio.estado()).isEqualTo(new EstadoWhatsappRespuesta(true, "alcanfores", "open", "51914995187"));
    }

    @Test
    @DisplayName("Conectar devuelve el QR, el código de emparejamiento y queda auditado")
    void conectar() {
        when(evolution.conectar("51914995187")).thenReturn(
                new EvolutionCliente.CodigoConexion("data:image/png;base64,QR", "WZYEH1YY", "connecting"));

        ConexionWhatsappRespuesta r = servicio.conectar("914 995 187");

        assertThat(r.qrBase64()).isEqualTo("data:image/png;base64,QR");
        assertThat(r.codigoEmparejamiento()).isEqualTo("WZYEH1YY");
        assertThat(r.mensaje()).contains("Dispositivos vinculados");
        verify(auditor).registrar(eq("CONECTAR_WHATSAPP"), eq("whatsapp"), eq("alcanfores"), contains("51914995187"));
    }

    @Test
    @DisplayName("Conectar cuando ya está conectado o el QR aún no está listo")
    void conectarOtrosCasos() {
        when(evolution.conectar(null)).thenReturn(new EvolutionCliente.CodigoConexion(null, null, "open"));
        assertThat(servicio.conectar(null).mensaje()).isEqualTo("El WhatsApp ya está conectado.");

        when(evolution.conectar(null)).thenReturn(new EvolutionCliente.CodigoConexion(null, null, "connecting"));
        assertThat(servicio.conectar(" ").mensaje()).contains("aún está preparando");
    }

    @Test
    @DisplayName("Desconectar cierra la sesión y se audita")
    void desconectar() {
        MensajeRespuesta r = servicio.desconectar();

        assertThat(r.estado()).isEqualTo("close");
        verify(evolution).desconectar();
        verify(auditor).registrar(eq("DESCONECTAR_WHATSAPP"), eq("whatsapp"), eq("alcanfores"), anyString());
    }

    @Test
    @DisplayName("Si Evolution falla, el panel recibe 503 WHATSAPP_NO_DISPONIBLE con el motivo")
    void evolutionCaido() {
        when(evolution.estado()).thenThrow(new EvolutionExcepcion(401, "Evolution API rechazó la clave (EVOLUTION_APIKEY)."));
        doThrow(new EvolutionExcepcion("No se pudo conectar con Evolution API", null)).when(evolution).desconectar();

        assertThatThrownBy(servicio::estado).isInstanceOf(NegocioExcepcion.class)
                .hasMessageContaining("EVOLUTION_APIKEY")
                .extracting("codigo").isEqualTo("WHATSAPP_NO_DISPONIBLE");
        assertThatThrownBy(servicio::desconectar).isInstanceOf(NegocioExcepcion.class).extracting("status").isEqualTo(503);
    }
}
