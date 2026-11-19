package pe.alcanfores.comun.auditoria;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.eventos.Eventos;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Datos que el {@link Auditor} manda a la bitácora central: actor, rol, ip, servicio, entidad y detalle. */
class AuditorTest {

    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final Auditor auditor = new Auditor(publicador, "reservas-service");

    @BeforeEach
    void preparar() {
        Tiempo.fijar(Clock.fixed(Instant.parse("2026-11-20T23:30:00Z"), Tiempo.ZONA));
    }

    @AfterEach
    void limpiar() {
        Tiempo.restablecer();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static void iniciarSesion(UsuarioToken u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.rol()))));
    }

    private static MockHttpServletRequest solicitud() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/pagos/5/validar");
        req.setRemoteAddr("10.0.0.7");
        RequestAttributes atributos = new ServletRequestAttributes(req);
        RequestContextHolder.setRequestAttributes(atributos);
        return req;
    }

    private AccionAuditadaEvento eventoPublicado() {
        ArgumentCaptor<AccionAuditadaEvento> captor = ArgumentCaptor.forClass(AccionAuditadaEvento.class);
        verify(publicador).publicar(eq(Eventos.AUDITORIA_ACCION), captor.capture());
        return captor.getValue();
    }

    @Test
    void registraAlAdministradorAutenticadoConSuIp() {
        iniciarSesion(new UsuarioToken(7L, "rhuaman", "Rosa Huamán", UsuarioToken.ADMIN));
        solicitud().addHeader("X-Forwarded-For", " 203.0.113.5 , 10.0.0.1");

        auditor.registrar("VALIDAR_PAGO", "pago", 5L, "Reserva ALC-7K3M9P · S/ 40.00");

        AccionAuditadaEvento e = eventoPublicado();
        assertThat(e.idEvento()).isNotNull();
        assertThat(e.ocurridoEn()).isEqualTo(LocalDateTime.of(2026, 11, 20, 18, 30));
        assertThat(e.servicio()).isEqualTo("reservas-service");
        assertThat(e.idActor()).isEqualTo(7L);
        assertThat(e.actor()).isEqualTo("rhuaman");
        assertThat(e.rol()).isEqualTo("ADMIN");
        assertThat(e.accion()).isEqualTo("VALIDAR_PAGO");
        assertThat(e.entidad()).isEqualTo("pago");
        assertThat(e.idEntidad()).isEqualTo("5");
        assertThat(e.detalle()).isEqualTo("Reserva ALC-7K3M9P · S/ 40.00");
        assertThat(e.ip()).isEqualTo("203.0.113.5"); // primera IP de X-Forwarded-For
    }

    @Test
    void sinSesionElActorEsElSistemaYLaIpEsLaRemota() {
        solicitud();
        auditor.registrar("VENCER_RESERVAS", "reserva", null, null);

        AccionAuditadaEvento e = eventoPublicado();
        assertThat(e.idActor()).isNull();
        assertThat(e.actor()).isEqualTo("Sistema");
        assertThat(e.rol()).isEqualTo("SISTEMA");
        assertThat(e.idEntidad()).isNull();
        assertThat(e.detalle()).isNull();
        assertThat(e.ip()).isEqualTo("10.0.0.7");
    }

    @Test
    void cabeceraXForwardedForEnBlancoUsaLaIpRemota() {
        solicitud().addHeader("X-Forwarded-For", "   ");
        auditor.registrar("X", "y", 1, "d");
        assertThat(eventoPublicado().ip()).isEqualTo("10.0.0.7");
    }

    @Test
    void fueraDeUnaSolicitudHttpLaIpEsNula() {
        auditor.registrar("TAREA_PROGRAMADA", "reserva", "ALC-7K3M9P", "Vencida");
        AccionAuditadaEvento e = eventoPublicado();
        assertThat(e.ip()).isNull();
        assertThat(e.idEntidad()).isEqualTo("ALC-7K3M9P");
    }

    @Test
    void principalQueNoEsUsuarioTokenCuentaComoSistema() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("servicio-interno",
                null, List.of(new SimpleGrantedAuthority("ROLE_INTERNO"))));
        auditor.registrar("SINCRONIZAR", "cancha", 2L, null);
        assertThat(eventoPublicado().actor()).isEqualTo("Sistema");
    }

    @Test
    void detalleLargoSeRecortaA500Caracteres() {
        auditor.registrar("EDITAR", "cancha", 2L, "x".repeat(800));
        String detalle = eventoPublicado().detalle();
        assertThat(detalle).hasSize(500).endsWith("...");
    }

    @Test
    void detalleDeExactamente500NoSeRecorta() {
        auditor.registrar("EDITAR", "cancha", 2L, "x".repeat(500));
        assertThat(eventoPublicado().detalle()).isEqualTo("x".repeat(500));
    }

    @Test
    void registrarComoUsaElActorIndicado() {
        iniciarSesion(new UsuarioToken(7L, "rhuaman", "Rosa Huamán", UsuarioToken.ADMIN)); // se ignora a propósito
        solicitud();
        auditor.registrarComo("Cliente 70123321", "CLIENTE", "CANCELAR_RESERVA", "reserva", 12L, "Lluvia");

        AccionAuditadaEvento e = eventoPublicado();
        assertThat(e.idActor()).isNull();
        assertThat(e.actor()).isEqualTo("Cliente 70123321");
        assertThat(e.rol()).isEqualTo("CLIENTE");
        assertThat(e.accion()).isEqualTo("CANCELAR_RESERVA");
        assertThat(e.idEntidad()).isEqualTo("12");
        assertThat(e.detalle()).isEqualTo("Lluvia");
        assertThat(e.ip()).isEqualTo("10.0.0.7");
        assertThat(e.servicio()).isEqualTo("reservas-service");
    }

    @Test
    void registrarComoConIdNulo() {
        auditor.registrarComo("Sistema", "SISTEMA", "LIMPIAR", "token", null, null);
        assertThat(eventoPublicado().idEntidad()).isNull();
    }
}
