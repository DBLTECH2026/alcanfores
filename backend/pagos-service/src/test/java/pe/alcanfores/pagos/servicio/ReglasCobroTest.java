package pe.alcanfores.pagos.servicio;

import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.modelo.TipoPago;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del cobro en caja (SPEC §3: monto > 0, ≤ saldo, 2 decimales) y coherencia del tipo de pago. */
class ReglasCobroTest {

    static final BigDecimal ADELANTO = new BigDecimal("45.00");

    static BigDecimal s(String v) {
        return new BigDecimal(v);
    }

    @Test
    void montoMayorQueElSaldoEs400ConElSaldoEnElMensaje() {
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.SALDO, s("50.00"), s("45.00"), false, ADELANTO))
                .isInstanceOfSatisfying(NegocioExcepcion.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(400);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("El monto no puede superar el saldo de S/ 45.00.");
                    org.assertj.core.api.Assertions.assertThat(e.getErrores().get(0).campo()).isEqualTo("monto");
                });
    }

    @Test
    void sinSaldoEs409() {
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.SALDO, s("1.00"), s("0.00"), false, ADELANTO))
                .isInstanceOfSatisfying(NegocioExcepcion.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCodigo()).isEqualTo("SIN_SALDO"));
    }

    @Test
    void tipoDePagoCoherenteConLoYaCobrado() {
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.SALDO, s("45.00"), s("90.00"), true, ADELANTO))
                .hasMessageContaining("aún no tiene adelanto");
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.ADELANTO, s("45.00"), s("45.00"), false, ADELANTO))
                .hasMessageContaining("cobra el saldo");
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.ADELANTO, s("20.00"), s("90.00"), true, ADELANTO))
                .hasMessage("El adelanto mínimo es S/ 45.00.");
        assertThatThrownBy(() -> PagoServicioImpl.validarCobro(TipoPago.TOTAL, s("80.00"), s("90.00"), true, ADELANTO))
                .hasMessage("Para el pago total el monto debe ser S/ 90.00.");
        assertThatCode(() -> PagoServicioImpl.validarCobro(TipoPago.TOTAL, s("90.00"), s("90.00"), true, ADELANTO))
                .doesNotThrowAnyException();
        assertThatCode(() -> PagoServicioImpl.validarCobro(TipoPago.SALDO, s("20.00"), s("45.00"), false, ADELANTO))
                .doesNotThrowAnyException();
    }

    @Test
    void urlsLocalesParaMercadoPago() {
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.esLocal("http://localhost:5186/pago")).isTrue();
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.esLocal("http://127.0.0.1:8086")).isTrue();
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.esLocal("https://alcanfores.pe/api")).isFalse();
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.esLocal("no es url ::")).isTrue();
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.motivoMercadoPago("cc_rejected_high_risk"))
                .contains("seguridad");
        org.assertj.core.api.Assertions.assertThat(IntentoPagoServicioImpl.motivoMercadoPago(null))
                .isEqualTo("Mercado Pago rechazó el pago.");
    }
}
