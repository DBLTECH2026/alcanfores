package pe.alcanfores.gateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void separaLosOrigenesPorComaYQuitaLaBarraFinal() {
        assertThat(CorsConfig.origenes(" https://alcanfores.pe/ ,http://localhost:5186,,http://localhost:5186"))
                .containsExactly("https://alcanfores.pe", "http://localhost:5186");
        assertThat(CorsConfig.origenes("")).isEmpty();
        assertThat(CorsConfig.origenes(null)).isEmpty();
    }
}
