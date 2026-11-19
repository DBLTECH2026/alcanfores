package pe.alcanfores.comun.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.util.Tiempo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fábricas de {@link NegocioExcepcion} y el cuerpo {@link ErrorApi}. */
class NegocioExcepcionTest {

    @AfterEach
    void restablecerReloj() {
        Tiempo.restablecer();
    }

    @Test
    void fabricasConStatusYCodigo() {
        NegocioExcepcion e = NegocioExcepcion.noEncontrado("No existe la reserva.");
        assertThat(e.getStatus()).isEqualTo(404);
        assertThat(e.getCodigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(e.getMessage()).isEqualTo("No existe la reserva.");
        assertThat(e.getErrores()).isEmpty();

        assertThat(NegocioExcepcion.conflicto("TURNO_OCUPADO", "x").getStatus()).isEqualTo(409);
        assertThat(NegocioExcepcion.regla("FUERA_DE_PLAZO", "x")).extracting(NegocioExcepcion::getStatus,
                NegocioExcepcion::getCodigo).containsExactly(422, "FUERA_DE_PLAZO");
        assertThat(NegocioExcepcion.noDisponible("SUNAT_CAIDA", "x").getStatus()).isEqualTo(503);

        NegocioExcepcion v = NegocioExcepcion.validacion("cliente.dni", "DNI inválido");
        assertThat(v.getStatus()).isEqualTo(400);
        assertThat(v.getCodigo()).isEqualTo("VALIDACION");
        assertThat(v.getErrores()).containsExactly(new ErrorCampo("cliente.dni", "DNI inválido"));
    }

    @Test
    void erroresNulosSeVuelvenListaVaciaYLaListaEsInmutable() {
        assertThat(new NegocioExcepcion(400, "X", "m", null).getErrores()).isEmpty();

        List<ErrorCampo> originales = new ArrayList<>(List.of(new ErrorCampo("a", "b")));
        NegocioExcepcion e = new NegocioExcepcion(400, "X", "m", originales);
        originales.add(new ErrorCampo("c", "d"));
        assertThat(e.getErrores()).hasSize(1);
        assertThatThrownBy(() -> e.getErrores().add(new ErrorCampo("e", "f")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void errorApiUsaElRelojDeLimaYNuncaDevuelveErroresNulos() {
        Tiempo.fijar(Clock.fixed(Instant.parse("2026-11-20T23:30:00.123Z"), Tiempo.ZONA));
        ErrorApi e = ErrorApi.de(404, "NO_ENCONTRADO", "No existe.", "/api/x");
        assertThat(e.timestamp()).isEqualTo(LocalDateTime.of(2026, 11, 20, 18, 30, 0));
        assertThat(e.status()).isEqualTo(404);
        assertThat(e.codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(e.mensaje()).isEqualTo("No existe.");
        assertThat(e.ruta()).isEqualTo("/api/x");
        assertThat(e.errores()).isEmpty();
        assertThat(ErrorApi.de(400, "V", "m", "/r", null).errores()).isNotNull().isEmpty();
        assertThat(ErrorApi.de(400, "V", "m", "/r", List.of(new ErrorCampo("c", "m"))).errores()).hasSize(1);
    }
}
