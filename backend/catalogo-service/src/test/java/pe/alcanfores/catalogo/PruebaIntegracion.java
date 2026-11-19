package pe.alcanfores.catalogo;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.alcanfores.catalogo.cliente.ReservasCliente;
import pe.alcanfores.comun.eventos.PublicadorEventos;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;

/**
 * Base de las pruebas de integración: contexto completo con la BD de pruebas recreada por Flyway (V1 + V2) una vez por
 * ejecución, RabbitMQ y reservas-service simulados con Mockito, y tokens reales firmados con el secreto de desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaIntegracion.BdLimpia.class)
public abstract class PruebaIntegracion {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JwtServicio jwt;

    @MockitoBean
    protected PublicadorEventos publicador;

    @MockitoBean
    protected ReservasCliente reservas;

    protected String tokenAdmin() {
        return "Bearer " + jwt.generar(new UsuarioToken(1L, "rhuaman", "Rosa Huamán", UsuarioToken.ADMIN));
    }

    protected String tokenRecepcion() {
        return "Bearer " + jwt.generar(new UsuarioToken(2L, "mtorres", "Marco Torres", UsuarioToken.RECEPCION));
    }

    @TestConfiguration
    static class BdLimpia {
        @Bean
        FlywayMigrationStrategy limpiarYMigrar() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
