package pe.alcanfores.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Valores por defecto del gateway que dependen de la configuración (CORS en local y tiempos por ruta). */
class ValoresPorDefectoTest {

    private static Properties yml() {
        YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
        fabrica.setResources(new ClassPathResource("application.yml"));
        return fabrica.getObject();
    }

    @Test
    void enLocalCorsAceptaElPortalYElPanel() {
        String valor = yml().getProperty("alcanfores.front-url");
        assertThat(valor).startsWith("${FRONT_URL:");
        String porDefecto = valor.substring("${FRONT_URL:".length(), valor.length() - 1);
        assertThat(CorsConfig.origenes(porDefecto)).containsExactly("http://localhost:5186", "http://localhost:5187");
    }

    @Test
    void laConsultaDeDocumentosTieneSuPropiaRutaCon20Segundos() {
        Properties p = yml();
        assertThat(p.getProperty("alcanfores.gateway.timeout-documentos-ms")).isEqualTo("20000");
        boolean hayRuta = false;
        for (int i = 0; p.getProperty("spring.cloud.gateway.server.webflux.routes[" + i + "].id") != null; i++) {
            String prefijo = "spring.cloud.gateway.server.webflux.routes[" + i + "].";
            if ("reservas-documentos".equals(p.getProperty(prefijo + "id"))) {
                hayRuta = true;
                assertThat(p.getProperty(prefijo + "predicates[0]")).isEqualTo("Path=/api/documentos/**");
                assertThat(p.getProperty(prefijo + "metadata.response-timeout"))
                        .isEqualTo("${alcanfores.gateway.timeout-documentos-ms}");
                assertThat(p.getProperty(prefijo + "order")).isEqualTo("-1");
            }
        }
        assertThat(hayRuta).isTrue();
    }

    @Test
    void elCircuitoDeDocumentosEsperaMasQueElNormal() {
        GatewayPropiedades props = new GatewayPropiedades(DataSize.ofMegabytes(6), Duration.ofSeconds(10), 20_000, 20_000,
                60_000, List.of(), List.of());
        assertThat(CircuitoConfig.limite("reservas-documentos", props)).isEqualTo(Duration.ofSeconds(22));
        assertThat(CircuitoConfig.limite("reservas", props)).isEqualTo(Duration.ofSeconds(12));
        assertThat(CircuitoConfig.limite("pagos-archivos", props)).isEqualTo(Duration.ofSeconds(22));
        assertThat(CircuitoConfig.limite("asistente", props)).isEqualTo(Duration.ofSeconds(62));
        GatewayPropiedades sinValor = new GatewayPropiedades(null, null, 0, 0, 0, null, null);
        assertThat(sinValor.timeoutDocumentosMs()).isEqualTo(20_000);
        assertThat(sinValor.timeoutAsistenteMs()).isEqualTo(60_000);
    }

    @Test
    void ningunFiltroQuitaLaCabeceraAuthorization() {
        // La reseña con sesión (/api/catalogo/**) y «Mis reservas» necesitan que el token llegue al servicio.
        Properties p = yml();
        p.stringPropertyNames().stream()
                .filter(k -> k.contains("filters"))
                .forEach(k -> assertThat(p.getProperty(k).toLowerCase()).doesNotContain("authorization"));
    }

    @Test
    void rutasNuevasDeLaV2() {
        Properties p = yml();
        String catalogo = null;
        String reservas = null;
        for (int i = 0; p.getProperty("spring.cloud.gateway.server.webflux.routes[" + i + "].id") != null; i++) {
            String prefijo = "spring.cloud.gateway.server.webflux.routes[" + i + "].";
            if ("catalogo".equals(p.getProperty(prefijo + "id"))) {
                catalogo = p.getProperty(prefijo + "predicates[0]");
            }
            if ("reservas".equals(p.getProperty(prefijo + "id"))) {
                reservas = p.getProperty(prefijo + "predicates[0]");
            }
        }
        assertThat(catalogo).contains("/api/admin/calendario/**");
        assertThat(reservas).contains("/api/cliente/**");
    }

    @Test
    void rutasDeRecuperarContrasenaYCorreo() {
        Properties p = yml();
        String notificaciones = null;
        String lentas = null;
        boolean recuperar = false;
        for (int i = 0; p.getProperty("spring.cloud.gateway.server.webflux.routes[" + i + "].id") != null; i++) {
            String prefijo = "spring.cloud.gateway.server.webflux.routes[" + i + "].";
            String id = p.getProperty(prefijo + "id");
            if ("notificaciones".equals(id)) {
                notificaciones = p.getProperty(prefijo + "predicates[0]");
            }
            if ("notificaciones-lentas".equals(id)) {
                lentas = p.getProperty(prefijo + "predicates[0]");
            }
            if ("auth-lentas".equals(id)) {
                recuperar = true;
                assertThat(p.getProperty(prefijo + "predicates[0]")).isEqualTo("Path=/api/auth/recuperar/**");
                assertThat(p.getProperty(prefijo + "order")).isEqualTo("-1");
                assertThat(p.getProperty(prefijo + "metadata.response-timeout")).isEqualTo("${alcanfores.gateway.timeout-archivos-ms}");
            }
        }
        assertThat(recuperar).isTrue();
        assertThat(notificaciones).contains("/api/admin/correo/**");
        assertThat(lentas).contains("/api/admin/correo/prueba");
        boolean limite = false;
        for (int i = 0; p.getProperty("alcanfores.gateway.limites[" + i + "].nombre") != null; i++) {
            if ("recuperar".equals(p.getProperty("alcanfores.gateway.limites[" + i + "].nombre"))) {
                limite = true;
                assertThat(p.getProperty("alcanfores.gateway.limites[" + i + "].por-minuto")).isEqualTo("5");
                assertThat(p.getProperty("alcanfores.gateway.limites[" + i + "].rutas[0]")).isEqualTo("/api/auth/recuperar/**");
                assertThat(p.getProperty("alcanfores.gateway.limites[" + i + "].metodo")).isNull();
            }
        }
        assertThat(limite).isTrue();
    }
}
