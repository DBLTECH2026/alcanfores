package pe.alcanfores.gateway.filtro;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RutaSolicitudTest {

    @ParameterizedTest
    @CsvSource({
            "/api/reservas, /api/reservas",
            "/api/reservas/../../interno/x, /interno/x",
            "/api/reservas/..;/..;/interno/x, /interno/x",
            "/%69nterno/x, /interno/x",
            "/api/%252e%252e/%252e%252e/interno, /interno",
            "//api//admin///usuarios, /api/admin/usuarios",
            "/API/Admin/Usuarios, /api/admin/usuarios"
    })
    void normalizaComoLoHariaElServicioDeDestino(String entrada, String esperado) {
        assertThat(RutaSolicitud.normalizar(entrada)).isEqualTo(esperado);
    }

    @org.junit.jupiter.api.Test
    void prefijosExactos() {
        assertThat(RutaSolicitud.empiezaCon("/interno", "/interno")).isTrue();
        assertThat(RutaSolicitud.empiezaCon("/interno/x", "/interno")).isTrue();
        assertThat(RutaSolicitud.empiezaCon("/internos", "/interno")).isFalse();
        assertThat(RutaSolicitud.normalizar("")).isEqualTo("/");
    }
}
