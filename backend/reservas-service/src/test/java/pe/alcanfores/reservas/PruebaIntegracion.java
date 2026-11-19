package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.reservas.cliente.CanchaCatalogo;
import pe.alcanfores.reservas.cliente.CatalogoCliente;
import pe.alcanfores.reservas.cliente.PagosCliente;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.repositorio.ReservaRepositorio;
import pe.alcanfores.reservas.repositorio.TurnoRepositorio;
import pe.alcanfores.reservas.servicio.ComplejoServicio;
import pe.alcanfores.reservas.servicio.TurnoServicio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas de integración: contexto completo contra MariaDB/MySQL ({@code alcanfores_reservas_test},
 * Flyway clean + migrate al iniciar), sin RabbitMQ ni Eureka (publicador y clientes HTTP simulados) y con el reloj de
 * {@link Tiempo} fijado al viernes 20/11/2026 10:40 (Lima).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaIntegracion.FlywayLimpio.class)
public abstract class PruebaIntegracion {

    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 10, 40);
    public static final LocalDate HOY = AHORA.toLocalDate();
    protected static final String TOKEN_INTERNO = "token-interno-solo-para-desarrollo-local";

    @MockitoBean
    protected PublicadorEventos publicador;
    @MockitoBean
    protected CatalogoCliente catalogo;
    @MockitoBean
    protected PagosCliente pagos;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected JwtServicio jwt;
    @Autowired
    protected TurnoServicio turnoServicio;
    @Autowired
    protected TurnoRepositorio turnos;
    @Autowired
    protected ReservaRepositorio reservas;
    @Autowired
    protected ComplejoServicio complejo;

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM reserva_historial");
        jdbc.update("DELETE FROM reserva_turno");
        jdbc.update("DELETE FROM cancelacion");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM turno");
        jdbc.update("DELETE FROM cliente");
        jdbc.update("DELETE FROM consulta_documento");
        jdbc.update("DELETE FROM evento_procesado");
        fijarReloj(AHORA);
        when(catalogo.configuracion()).thenReturn(Map.of());
        when(catalogo.canchas()).thenReturn(CanchaCatalogo.semilla());
        complejo.refrescar();
        complejo.refrescarCalendario();
    }

    @AfterEach
    void restablecerReloj() {
        Tiempo.restablecer();
    }

    protected static void fijarReloj(LocalDateTime momento) {
        Tiempo.fijar(Clock.fixed(momento.atZone(Tiempo.ZONA).toInstant(), Tiempo.ZONA));
    }

    /** Genera los turnos de un día para todas las canchas y devuelve el de la cancha y hora pedidas. */
    protected Turno turno(long idCancha, LocalDate fecha, int hora) {
        turnoServicio.generarRango(fecha, fecha, List.of());
        return turnos.findByFechaOrderByIdCanchaAscHoraInicioAsc(fecha).stream()
                .filter(t -> t.getIdCancha() == idCancha && t.getHoraInicio().equals(LocalTime.of(hora, 0)))
                .findFirst().orElseThrow();
    }

    protected String token(String rol) {
        long id = "ADMIN".equals(rol) ? 1L : 2L;
        return "Bearer " + jwt.generar(new UsuarioToken(id, "ADMIN".equals(rol) ? "rhuaman" : "mtorres",
                "ADMIN".equals(rol) ? "Rosa Huamán" : "Marco Torres", rol));
    }

    protected static String cuerpoReserva(long idTurno, String dni) {
        return """
                {"idTurno": %d, "cliente": {"dni": "%s", "nombres": "diego  armando", "apellidos": "ramos quispe",
                 "telefono": "+51 987 654 321", "correo": "Diego.Ramos@Example.com"},
                 "metodoPago": "YAPE", "aceptaTerminos": true, "aceptaPrivacidad": true}
                """.formatted(idTurno, dni);
    }

    /** Registra una reserva web por la API y devuelve el JSON de respuesta. */
    protected JsonNode reservarWeb(long idTurno, String dni) throws Exception {
        MvcResult r = mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(idTurno, dni)))
                .andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("No se pudo reservar: " + r.getResponse().getContentAsString());
        }
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    protected static String cuerpoReservaVarios(List<Long> ids, String dni) {
        String lista = ids.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
        return """
                {"idsTurno": [%s], "cliente": {"dni": "%s", "nombres": "diego  armando", "apellidos": "ramos quispe",
                 "telefono": "+51 987 654 321", "correo": "Diego.Ramos@Example.com"},
                 "metodoPago": "YAPE", "aceptaTerminos": true, "aceptaPrivacidad": true}
                """.formatted(lista, dni);
    }

    /** Registra una reserva web de varios turnos por la API y devuelve el JSON de respuesta. */
    protected JsonNode reservarWebVarios(List<Long> ids, String dni) throws Exception {
        MvcResult r = mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoReservaVarios(ids, dni))).andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("No se pudo reservar: " + r.getResponse().getContentAsString());
        }
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    /** Token de un cliente con cuenta (rol CLIENTE y su DNI). */
    protected String tokenCliente(String dni) {
        return "Bearer " + jwt.generar(new UsuarioToken(30L, "cliente@example.com", "Cliente Prueba", UsuarioToken.CLIENTE, dni));
    }

    /** Flyway limpia y migra la BD de pruebas una vez por contexto. */
    @TestConfiguration
    static class FlywayLimpio {
        @Bean
        FlywayMigrationStrategy limpiarYMigrar() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
