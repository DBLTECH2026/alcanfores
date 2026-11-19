package pe.alcanfores.auth.servicio;

import org.junit.jupiter.api.Test;
import pe.alcanfores.auth.config.AuthPropiedades;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroIntentosAnonimosTest {

    private final RegistroIntentosAnonimos registro = new RegistroIntentosAnonimos(new AuthPropiedades(4, 5, 15, null));
    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 20, 19, 0);

    @Test
    void bloqueaAlQuintoFalloYLiberaAlTerminarElPlazo() {
        for (int i = 0; i < 4; i++) {
            assertThat(registro.registrarFallo("nadie", t0)).isEmpty();
        }
        assertThat(registro.registrarFallo("nadie", t0)).contains(t0.plusMinutes(15));
        assertThat(registro.bloqueadoHasta("nadie", t0.plusMinutes(14))).isPresent();
        assertThat(registro.bloqueadoHasta("nadie", t0.plusMinutes(15))).isEmpty();
        // Tras el bloqueo la cuenta vuelve a empezar desde uno.
        assertThat(registro.registrarFallo("nadie", t0.plusMinutes(16))).isEmpty();
    }

    @Test
    void laLimpiezaQuitaEntradasViejas() {
        registro.registrarFallo("a", t0);
        registro.registrarFallo("b", t0.plusMinutes(20));
        registro.limpiar(t0.plusMinutes(30));
        assertThat(registro.tamanio()).isEqualTo(1);
        assertThat(registro.bloqueadoHasta("a", t0.plusMinutes(30))).isEmpty();
    }

    @Test
    void usuariosDistintosNoSeMezclan() {
        for (int i = 0; i < 5; i++) {
            registro.registrarFallo("uno", t0);
        }
        assertThat(registro.bloqueadoHasta("uno", t0)).isPresent();
        assertThat(registro.bloqueadoHasta("dos", t0)).isEmpty();
    }
}
