package pe.alcanfores.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DiscoveryServerAplicacionTest {

    @Autowired
    private TestRestTemplate http;

    @Value("${eureka.server.enable-self-preservation}")
    private boolean autopreservacion;

    @Test
    void laSaludRespondeUp() {
        ResponseEntity<String> r = http.getForEntity("/actuator/health", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void elRegistroNoSeIncluyeASiMismo() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> r = http.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getBody()).doesNotContainIgnoringCase("DISCOVERY-SERVER");
    }

    @Test
    void laAutopreservacionSeDesactivaPorPropiedad() {
        assertThat(autopreservacion).isFalse();
    }
}
