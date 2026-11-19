package pe.alcanfores.notificaciones.servicio.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.canal.CanalEnvio;
import pe.alcanfores.notificaciones.canal.WhatsappCanal;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;
import pe.alcanfores.notificaciones.cliente.PagosCliente;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoAdjunto;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Envío por WhatsApp con Evolution API simulada (MockRestServiceServer) y el resto con mocks. */
class EnvioServicioImplTest {

    private static final String URL = "https://evolution.prueba.pe";
    private static final String OK = "{\"key\":{\"id\":\"BAE5\"}}";

    private MockRestServiceServer evolution;
    private ReservasCliente reservas;
    private PagosCliente pagos;
    private CanalEnvio correo;
    private EnvioServicioImpl servicio;

    @BeforeEach
    void preparar() {
        Datos.reloj(LocalDateTime.of(2026, 11, 19, 10, 0));
        NotificacionesPropiedades p = new NotificacionesPropiedades();
        p.getEvolution().setUrl(URL);
        p.getEvolution().setApikey("clave");
        RestClient.Builder builder = RestClient.builder();
        evolution = MockRestServiceServer.bindTo(builder).build();
        WhatsappCanal whatsapp = new WhatsappCanal(new EvolutionCliente(builder, p));

        correo = mock(CanalEnvio.class);
        when(correo.canal()).thenReturn(CanalNotificacion.CORREO);
        reservas = mock(ReservasCliente.class);
        pagos = mock(PagosCliente.class);
        NotificacionRepositorio repo = mock(NotificacionRepositorio.class);
        when(repo.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new EnvioServicioImpl(List.of(whatsapp, correo), reservas, pagos, repo);
    }

    @AfterEach
    void restablecer() {
        Tiempo.restablecer();
    }

    private static Notificacion whatsapp(List<Adjunto> adjuntos) {
        return new Notificacion(Datos.CODIGO, "reserva.confirmada", CanalNotificacion.WHATSAPP, "51987654321",
                "Reserva confirmada", "*Reserva confirmada*\n\nHola, Diego.", adjuntos, Tiempo.ahora());
    }

    @Test
    @DisplayName("Sin adjuntos: sendText → ENVIADO con 1 intento y fecha de envío")
    void textoEnviado() {
        evolution.expect(requestTo(URL + "/message/sendText/alcanfores"))
                .andExpect(jsonPath("$.text").value("*Reserva confirmada*\n\nHola, Diego."))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        Notificacion n = servicio.enviar(whatsapp(List.of()));

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getEnviadoEn()).isEqualTo(LocalDateTime.of(2026, 11, 19, 10, 0));
        assertThat(n.getUltimoError()).isNull();
        evolution.verify();
    }

    @Test
    @DisplayName("Con la constancia: un solo sendMedia con el PDF y el mensaje como leyenda")
    void documentoConLeyenda() {
        when(reservas.constanciaPdf(Datos.CODIGO)).thenReturn(Datos.PDF);
        evolution.expect(ExpectedCount.once(), requestTo(URL + "/message/sendMedia/alcanfores"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.mediatype").value("document"))
                .andExpect(jsonPath("$.fileName").value("constancia-ALC-7K3M9P.pdf"))
                .andExpect(jsonPath("$.caption").value("*Reserva confirmada*\n\nHola, Diego."))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        Notificacion n = servicio.enviar(whatsapp(List.of(Adjunto.constancia(Datos.CODIGO))));

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(n.getAdjuntos()).extracting(Adjunto::estado).containsExactly(EstadoAdjunto.ADJUNTADO);
        evolution.verify();
    }

    @Test
    @DisplayName("Texto más largo que la leyenda: primero el texto y luego el PDF")
    void textoLargoYDocumento() {
        when(reservas.constanciaPdf(Datos.CODIGO)).thenReturn(Datos.PDF);
        Notificacion larga = new Notificacion(Datos.CODIGO, "reserva.confirmada", CanalNotificacion.WHATSAPP, "51987654321",
                "Asunto", "x".repeat(1200), List.of(Adjunto.constancia(Datos.CODIGO)), Tiempo.ahora());
        evolution.expect(requestTo(URL + "/message/sendText/alcanfores")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        evolution.expect(requestTo(URL + "/message/sendMedia/alcanfores"))
                .andExpect(jsonPath("$.caption").value("constancia-ALC-7K3M9P.pdf"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(servicio.enviar(larga).getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        evolution.verify();
    }

    @Test
    @DisplayName("Error de Evolution → FALLIDO, intento sumado y último error; el siguiente intento vuelve a sumar")
    void errorFallido() {
        evolution.expect(ExpectedCount.twice(), requestTo(URL + "/message/sendText/alcanfores")).andRespond(withServerError());

        Notificacion n = servicio.enviar(whatsapp(List.of()));
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getUltimoError()).contains("Evolution API respondió 500");
        assertThat(n.getEnviadoEn()).isNull();
        assertThat(n.reintentable(3)).isTrue();

        n = servicio.enviar(n);
        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
    }

    @Test
    @DisplayName("Error al enviar el documento → FALLIDO (no se pierde el mensaje: el reintento lo manda completo)")
    void errorDocumento() {
        when(reservas.constanciaPdf(Datos.CODIGO)).thenReturn(Datos.PDF);
        evolution.expect(requestTo(URL + "/message/sendMedia/alcanfores")).andRespond(withServerError());

        Notificacion n = servicio.enviar(whatsapp(List.of(Adjunto.constancia(Datos.CODIGO))));

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(n.getIntentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("PDF no descargable: se envía solo el texto y el adjunto queda NO_DISPONIBLE")
    void pdfNoDisponible() {
        when(reservas.constanciaPdf(Datos.CODIGO)).thenThrow(NegocioExcepcion.noEncontrado("No existe"));
        evolution.expect(requestTo(URL + "/message/sendText/alcanfores")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        Notificacion n = servicio.enviar(whatsapp(List.of(Adjunto.constancia(Datos.CODIGO))));

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(n.getAdjuntos()).singleElement().satisfies(a -> {
            assertThat(a.estado()).isEqualTo(EstadoAdjunto.NO_DISPONIBLE);
            assertThat(a.detalle()).contains("No existe");
        });
        evolution.verify();
    }

    @Test
    @DisplayName("Respuesta que no es PDF: no se adjunta")
    void noEsPdf() {
        when(pagos.comprobantePdf(33L)).thenReturn("<html>error</html>".getBytes(StandardCharsets.UTF_8));
        when(correo.configurado()).thenReturn(false);
        Notificacion n = new Notificacion(Datos.CODIGO, "comprobante.emitido", CanalNotificacion.CORREO, "a@b.pe", "x", "<p>x</p>",
                List.of(Adjunto.comprobante(33L, "B001-00000001")), Tiempo.ahora());

        n = servicio.enviar(n);

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(n.getAdjuntos().get(0).detalle()).isEqualTo("El archivo recibido no es un PDF válido.");
        assertThat(EnvioServicioImpl.esPdf(null)).isFalse();
        assertThat(EnvioServicioImpl.esPdf(Datos.PDF)).isTrue();
    }

    @Test
    @DisplayName("Varios avisos del mismo hecho descargan el PDF una sola vez")
    void descargaUnaVez() {
        when(reservas.constanciaPdf(Datos.CODIGO)).thenReturn(Datos.PDF);
        when(correo.configurado()).thenReturn(true);
        evolution.expect(requestTo(URL + "/message/sendMedia/alcanfores")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        Notificacion mail = new Notificacion(Datos.CODIGO, "reserva.confirmada", CanalNotificacion.CORREO, "a@b.pe", "x",
                "<p>x</p>", List.of(Adjunto.constancia(Datos.CODIGO)), Tiempo.ahora());

        List<Notificacion> r = servicio.enviarTodas(List.of(mail, whatsapp(List.of(Adjunto.constancia(Datos.CODIGO)))));

        assertThat(r).extracting(Notificacion::getEstado).containsOnly(EstadoNotificacion.ENVIADO);
        verify(reservas, times(1)).constanciaPdf(Datos.CODIGO);
    }

    @Test
    @DisplayName("Sin configuración del canal: SIMULADO y sin llamar al proveedor")
    void simulado() {
        NotificacionesPropiedades sinEvolution = new NotificacionesPropiedades();
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer nadie = MockRestServiceServer.bindTo(b).build();
        NotificacionRepositorio repo = mock(NotificacionRepositorio.class);
        when(repo.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));
        EnvioServicioImpl simulado = new EnvioServicioImpl(List.of(new WhatsappCanal(new EvolutionCliente(b, sinEvolution))),
                reservas, pagos, repo);

        Notificacion n = simulado.enviar(whatsapp(List.of()));

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.SIMULADO);
        assertThat(n.getIntentos()).isEqualTo(1);
        nadie.verify();
    }
}
