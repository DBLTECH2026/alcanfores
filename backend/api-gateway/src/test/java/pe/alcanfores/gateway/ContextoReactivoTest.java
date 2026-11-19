package pe.alcanfores.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pe.alcanfores.comun.seguridad.JwtServicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El gateway es WebFlux puro: comun aporta JwtServicio pero ninguna pieza Servlet llega al classpath ni al contexto. */
@SpringBootTest
@ActiveProfiles("test")
class ContextoReactivoTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void elContextoEsReactivoYTieneElJwtDeComun() {
        assertThat(contexto).isInstanceOf(ReactiveWebApplicationContext.class);
        assertThat(contexto.getBeansOfType(JwtServicio.class)).hasSize(1);
        assertThat(contexto.getBeanNamesForType(pe.alcanfores.comun.error.ManejadorGlobalErrores.class)).isEmpty();
    }

    @Test
    void noHaySpringWebMvcNiServletEnElClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.web.servlet.DispatcherServlet"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("jakarta.servlet.Filter"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
