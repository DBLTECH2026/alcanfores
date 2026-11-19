package pe.alcanfores.pagos.pasarela.simulador;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.alcanfores.comun.error.ErrorCampo;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorTarjetaTest {

    static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "5555555555554444", "378282246310005", "4000002760003184",
            "4000000000000002", "4000000000009995", "4000000000000069", "36227206271667", "2223003122003222"})
    void lasTarjetasDePruebaPasanLuhn(String numero) {
        assertThat(ValidadorTarjeta.luhn(numero)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111112", "1234567812345678", "", "41111111a1111111"})
    void numerosAlteradosNoPasanLuhn(String numero) {
        assertThat(ValidadorTarjeta.luhn(numero)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"4111111111111111,VISA", "5105105105105100,MASTERCARD", "2221000000000009,MASTERCARD",
            "2720999999999996,MASTERCARD", "340000000000009,AMEX", "378282246310005,AMEX", "36227206271667,DINERS",
            "38520000023237,DINERS"})
    void detectaLaMarca(String numero, MarcaTarjeta marca) {
        assertThat(MarcaTarjeta.detectar(numero)).contains(marca);
    }

    @Test
    void marcasNoAceptadas() {
        assertThat(MarcaTarjeta.detectar("6011111111111117")).isEmpty();
        assertThat(MarcaTarjeta.detectar("2720999999999996")).isPresent();
        assertThat(MarcaTarjeta.detectar("2721000000000000")).isEmpty();
        assertThat(MarcaTarjeta.detectar("9")).isEmpty();
    }

    @Test
    void tarjetaCorrectaNoTieneErrores() {
        SolicitudTarjeta t = new SolicitudTarjeta("4111 1111 1111 1111", "Diego Ramos", 11, 26, "123", "diego@correo.pe");
        assertThat(ValidadorTarjeta.validar(t, NOV_2026)).isEmpty();
        assertThat(ValidadorTarjeta.validar(new SolicitudTarjeta("378282246310005", "Ana Pérez", 1, 2030, "1234",
                "ana@correo.pe"), NOV_2026)).isEmpty();
    }

    @Test
    void vencimientoPasadoOMesInvalido() {
        assertThat(campos(new SolicitudTarjeta("4111111111111111", "Diego Ramos", 10, 26, "123", "d@correo.pe")))
                .containsExactly("vencimiento");
        assertThat(campos(new SolicitudTarjeta("4111111111111111", "Diego Ramos", 13, 27, "123", "d@correo.pe")))
                .containsExactly("vencimiento");
        assertThat(campos(new SolicitudTarjeta("4111111111111111", "Diego Ramos", null, null, "123", "d@correo.pe")))
                .containsExactly("vencimiento");
    }

    @Test
    void cvvSegunLaMarcaYTitularYCorreo() {
        List<ErrorCampo> e = ValidadorTarjeta.validar(new SolicitudTarjeta("378282246310005", "D4", 12, 30, "123", "x"), NOV_2026);
        assertThat(e).extracting(ErrorCampo::campo).containsExactly("cvv", "titular", "correo");
        assertThat(e.get(0).mensaje()).isEqualTo("El CVV de American Express tiene 4 dígitos.");
        assertThat(campos(new SolicitudTarjeta("4111111111111111", "Diego Ramos", 12, 30, "1234", "d@correo.pe")))
                .containsExactly("cvv");
    }

    @Test
    void elToStringNuncaMuestraElNumeroNiElCvv() {
        SolicitudTarjeta t = new SolicitudTarjeta("4111 1111 1111 1111", "Diego Ramos", 12, 30, "987", "d@correo.pe");
        assertThat(t.toString()).doesNotContain("4111 1111 1111 1111", "4111111111111111", "987")
                .contains("**** **** **** 1111");
        assertThat(t.ultimos4()).isEqualTo("1111");
    }

    @Test
    void resultadosDeLasTarjetasDePrueba() {
        assertThat(TarjetasPrueba.resultado("4111111111111111")).isEqualTo(TarjetasPrueba.Resultado.APROBADA);
        assertThat(TarjetasPrueba.resultado("4000002760003184")).isEqualTo(TarjetasPrueba.Resultado.REQUIERE_3DS);
        assertThat(TarjetasPrueba.resultado("4000000000000002").motivo()).contains("Fondos insuficientes");
        assertThat(TarjetasPrueba.resultado("4012888888881881")).isEqualTo(TarjetasPrueba.Resultado.NO_RECONOCIDA);
        assertThat(TarjetasPrueba.lista()).hasSize(7);
    }

    private static List<String> campos(SolicitudTarjeta t) {
        return ValidadorTarjeta.validar(t, NOV_2026).stream().map(ErrorCampo::campo).toList();
    }
}
