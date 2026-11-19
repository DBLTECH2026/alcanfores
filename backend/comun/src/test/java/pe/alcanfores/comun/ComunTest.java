package pe.alcanfores.comun;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.seguridad.JwtServicio;
import pe.alcanfores.comun.seguridad.UsuarioToken;
import pe.alcanfores.comun.util.Dinero;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.validacion.Documentos;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComunTest {

    @Test
    void rucConDigitoVerificadorCorrectoEsValido() {
        assertThat(Documentos.rucValido("20612345679")).isTrue();   // emisor ficticio del sistema
        assertThat(Documentos.rucValido("20612345678")).isFalse();  // dígito verificador alterado
        assertThat(Documentos.rucValido("30612345679")).isFalse();  // prefijo inexistente
        assertThat(Documentos.rucValido("2061234567")).isFalse();   // 10 dígitos
        assertThat(Documentos.rucParaFactura("20612345679")).isTrue();
    }

    @Test
    void dniYCelular() {
        assertThat(Documentos.dniValido("70123321")).isTrue();
        assertThat(Documentos.dniValido("7012332")).isFalse();
        assertThat(Documentos.dniValido("7012332a")).isFalse();
        assertThat(Texto.normalizarCelular("+51 987 654 321")).isEqualTo("987654321");
        assertThat(Documentos.celularValido(Texto.normalizarCelular("987-654-321"))).isTrue();
        assertThat(Documentos.celularValido("887654321")).isFalse();
        assertThat(Documentos.enmascararDni("70123321")).isEqualTo("7****321");
    }

    @Test
    void textos() {
        assertThat(Texto.titulo("  rOSA   huamán  ")).isEqualTo("Rosa Huamán");
        assertThat(Texto.autorCorto("diego", "ramos quispe")).isEqualTo("Diego R.");
    }

    @Test
    void montoEnLetrasComoEnLosComprobantes() {
        assertThat(Dinero.enLetras(new BigDecimal("80"))).isEqualTo("OCHENTA CON 00/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("40.50"))).isEqualTo("CUARENTA CON 50/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("121"))).isEqualTo("CIENTO VEINTIUNO CON 00/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("100"))).isEqualTo("CIEN CON 00/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("1234.05"))).isEqualTo("MIL DOSCIENTOS TREINTA Y CUATRO CON 05/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("21000"))).isEqualTo("VEINTIUN MIL CON 00/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("0.90"))).isEqualTo("CERO CON 90/100 SOLES");
    }

    @Test
    void igvIncluidoCuadraAlCentimo() {
        BigDecimal total = new BigDecimal("80.00");
        BigDecimal base = Dinero.baseImponible(total);
        BigDecimal igv = Dinero.igvIncluido(total);
        assertThat(base).isEqualByComparingTo("67.80");
        assertThat(igv).isEqualByComparingTo("12.20");
        assertThat(base.add(igv)).isEqualByComparingTo(total);
        assertThat(Dinero.porcentaje(new BigDecimal("90.00"), 50)).isEqualByComparingTo("45.00");
    }

    @Test
    void jwtIdaYVuelta() {
        JwtServicio jwt = new JwtServicio("clave-de-prueba-de-al-menos-32-bytes-123456", 8);
        String token = jwt.generar(new UsuarioToken(7L, "rhuaman", "Rosa Huamán", "ADMIN"));
        UsuarioToken u = jwt.validar(token);
        assertThat(u.id()).isEqualTo(7L);
        assertThat(u.esAdmin()).isTrue();
        assertThatThrownBy(() -> jwt.validar(token + "x")).isInstanceOf(JwtException.class);
        JwtServicio otro = new JwtServicio("otra-clave-distinta-de-al-menos-32-bytes-99", 8);
        assertThatThrownBy(() -> otro.validar(token)).isInstanceOf(JwtException.class);
    }
}
