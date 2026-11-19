package pe.alcanfores.auth.validacion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContrasenaSeguraTest {

    @ParameterizedTest
    @ValueSource(strings = {"Alcanfores2026", "Recepcion2026", "Aa345678", "ÑandúVeloz9", "Clave.Segura-2026!"})
    void aceptaContrasenasQueCumplenLaPolitica(String clave) {
        assertThat(ContrasenaSegura.Validador.cumple(clave)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Corta1",            // menos de 8
            "sinmayuscula1",     // falta mayúscula
            "SINMINUSCULA1",     // falta minúscula
            "SinNumeroAqui",     // falta número
            " Espacio2026",      // espacio al inicio
            "Espacio2026 ",      // espacio al final
            "Aa1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" // 65 caracteres
    })
    void rechazaContrasenasDebiles(String clave) {
        assertThat(ContrasenaSegura.Validador.cumple(clave)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rechazaMasDe72BytesAunqueTengaMenosDe64Caracteres() {
        String clave = "Aa1" + "ñ".repeat(40); // 43 caracteres, 83 bytes en UTF-8
        assertThat(ContrasenaSegura.Validador.cumple(clave)).isFalse();
    }
}
