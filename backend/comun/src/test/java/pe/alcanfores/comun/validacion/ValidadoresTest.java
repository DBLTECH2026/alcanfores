package pe.alcanfores.comun.validacion;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Validadores peruanos (DNI, RUC, celular, nombres, código de reserva y texto libre) vía jakarta.validation. */
class ValidadoresTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    static class Datos {
        @Dni
        String dni;
        @Ruc
        String ruc;
        @Celular
        String celular;
        @NombrePersona
        String nombre;
        @CodigoReserva
        String codigo;
        @SinEnlaces
        String comentario;
    }

    private Set<ConstraintViolation<Datos>> validar(String propiedad, String valor) {
        return validador.validateValue(Datos.class, propiedad, valor);
    }

    private void esValido(String propiedad, String valor) {
        assertThat(validar(propiedad, valor)).as("%s=«%s» debería ser válido", propiedad, valor).isEmpty();
    }

    private void esInvalido(String propiedad, String valor, String mensaje) {
        assertThat(validar(propiedad, valor)).as("%s=«%s» debería ser inválido", propiedad, valor)
                .singleElement().extracting(ConstraintViolation::getMessage).isEqualTo(mensaje);
    }

    // ---------- @Dni ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"70123321", " 70123321 ", "00000000"})
    void dniValido(String valor) {
        esValido("dni", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "7012332", "701233210", "7012332a", "7012 3321"})
    void dniInvalido(String valor) {
        esInvalido("dni", valor, "El DNI debe tener 8 dígitos.");
    }

    // ---------- @Ruc ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "20000000001", "20131312955", " 20131312955 ",
            "20000200001",  // dígito verificador 11 → 1
            "20001100000",  // dígito verificador 10 → 0
            "15000000008"})
    void rucValido(String valor) {
        esValido("ruc", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20131312954", "20000000002", "30000000001", "2013131295", "201313129551", "2013131295a"})
    void rucInvalido(String valor) {
        esInvalido("ruc", valor, "El RUC no es válido.");
    }

    @Test
    void digitoVerificadorDelRuc() {
        assertThat(Documentos.digitoVerificadorRuc("2000000000")).isEqualTo(1);
        assertThat(Documentos.digitoVerificadorRuc("2013131295")).isEqualTo(5);
        assertThat(Documentos.digitoVerificadorRuc("2000020000")).isEqualTo(1);
        assertThat(Documentos.digitoVerificadorRuc("2000110000")).isEqualTo(0);
    }

    @Test
    void soloRuc10Y20PuedenRecibirFactura() {
        assertThat(Documentos.rucParaFactura("20000000001")).isTrue();
        assertThat(Documentos.rucParaFactura("20131312955")).isTrue();
        String ruc10 = "1012345678" + Documentos.digitoVerificadorRuc("1012345678");
        assertThat(Documentos.rucParaFactura(ruc10)).isTrue();
        assertThat(Documentos.rucValido("15000000008")).isTrue();
        assertThat(Documentos.rucParaFactura("15000000008")).isFalse();
        assertThat(Documentos.rucParaFactura("20131312954")).isFalse();
        assertThat(Documentos.rucValido(null)).isFalse();
    }

    // ---------- @Celular ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"987654321", "987 654 321", "987-654-321", "+51 987 654 321", "51987654321", "(987) 654321"})
    void celularValido(String valor) {
        esValido("celular", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "887654321", "98765432", "9876543210", "+52 987 654 321", "98765432a"})
    void celularInvalido(String valor) {
        esInvalido("celular", valor, "Ingresa un celular peruano de 9 dígitos que empiece con 9.");
    }

    // ---------- @NombrePersona ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Rosa", "Rosa Huamán", "  María   José  ", "O'Neill-Pérez", "Ñuñez", "Lü"})
    void nombreValido(String valor) {
        esValido("nombre", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "R", "Rosa123", "Rosa_H", "' -", "--", "Rosa@"})
    void nombreInvalido(String valor) {
        esInvalido("nombre", valor, "Usa solo letras (2 a 60).");
    }

    @Test
    void nombreDeMasDe60LetrasEsInvalido() {
        esValido("nombre", "a".repeat(60));
        esInvalido("nombre", "a".repeat(61), "Usa solo letras (2 a 60).");
    }

    // ---------- @CodigoReserva ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ALC-7K3M9P", " alc-7k3m9p ", "ALC-ABCDEF", "ALC-234567"})
    void codigoValido(String valor) {
        esValido("codigo", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ALC-7K3M9", "ALC-7K3M9PP", "ALC-7K3M9O", "ALC-7K3M9I", "ALC-123456", "ALC-0BCDEF",
            "ABC-7K3M9P", "ALC7K3M9P"})
    void codigoInvalido(String valor) {
        esInvalido("codigo", valor, "El código tiene el formato ALC-XXXXXX.");
    }

    @Test
    void elAlfabetoDelCodigoCoincideConElPatron() {
        assertThat(CodigoReserva.ALFABETO).doesNotContain("I", "O", "0", "1").hasSize(32);
        for (char c : CodigoReserva.ALFABETO.toCharArray()) {
            assertThat(CodigoReserva.PATRON.matcher("ALC-" + String.valueOf(c).repeat(6)).matches())
                    .as("letra %s", c).isTrue();
        }
    }

    // ---------- @SinEnlaces ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Excelente cancha, muy limpia y bien iluminada.", "OK BIEN", "Todo Muy Bien En La Cancha",
            "Buen trato; volveré el sábado.", "Precio justo: 80 soles."})
    void comentarioValido(String valor) {
        esValido("comentario", valor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Visiten www.otracancha.pe ahora", "Mira https://ejemplo.org/promo", "http://x",
            "Reserven en canchita.com que es mejor", "Escríbeme a mi-pagina.io", "PESIMA ATENCION NO VUELVO MAS"})
    void comentarioInvalido(String valor) {
        esInvalido("comentario", valor, "Escribe entre 10 y 500 caracteres, sin enlaces.");
    }

    // ---------- Documentos ----------

    @Test
    void enmascararDni() {
        assertThat(Documentos.enmascararDni("70123321")).isEqualTo("7****321");
        assertThat(Documentos.enmascararDni("1234")).isEqualTo("1234");
        assertThat(Documentos.enmascararDni("123")).isEqualTo("123");
        assertThat(Documentos.enmascararDni(null)).isNull();
    }

    @Test
    void dniYCelularNulosNoSonValidosEnDocumentos() {
        assertThat(Documentos.dniValido(null)).isFalse();
        assertThat(Documentos.celularValido(null)).isFalse();
    }
}
