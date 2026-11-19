package pe.alcanfores.notificaciones;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.PagosCliente;
import pe.alcanfores.notificaciones.cliente.ReservasCliente;
import pe.alcanfores.notificaciones.repositorio.EventoProcesadoRepositorio;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.repositorio.RecordatorioRepositorio;
import pe.alcanfores.notificaciones.servicio.ComplejoServicio;

import static org.mockito.Mockito.when;

/**
 * Base de las pruebas de integración: contexto completo con la BD de pruebas (MariaDB/MySQL {@code _test}), sin
 * RabbitMQ ni Eureka; los otros microservicios y el bus se reemplazan por mocks. Correo y WhatsApp sin configurar
 * (modo SIMULADO).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    @MockitoBean
    protected RabbitTemplate rabbitTemplate;
    @MockitoBean
    protected PublicadorEventos publicador;
    @MockitoBean
    protected Auditor auditor;
    @MockitoBean
    protected ReservasCliente reservasCliente;
    @MockitoBean
    protected PagosCliente pagosCliente;
    @MockitoBean
    protected ComplejoServicio complejoServicio;

    @Autowired
    protected NotificacionRepositorio notificaciones;
    @Autowired
    protected RecordatorioRepositorio recordatorios;
    @Autowired
    protected EventoProcesadoRepositorio eventosProcesados;

    @BeforeEach
    void limpiarBase() {
        notificaciones.deleteAll();
        recordatorios.deleteAll();
        eventosProcesados.deleteAll();
        when(complejoServicio.datos()).thenReturn(Datos.complejo());
    }

    @AfterEach
    void restablecerReloj() {
        Tiempo.restablecer();
    }
}
