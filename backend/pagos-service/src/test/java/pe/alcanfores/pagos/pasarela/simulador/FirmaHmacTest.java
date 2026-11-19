package pe.alcanfores.pagos.pasarela.simulador;

import org.junit.jupiter.api.Test;
import pe.alcanfores.pagos.servicio.FirmasMercadoPago;

import static org.assertj.core.api.Assertions.assertThat;

class FirmaHmacTest {

    @Test
    void vectorConocidoDeHmacSha256() {
        assertThat(FirmaHmac.firmar("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void firmaValidaEInvalida() {
        String cuerpo = "{\"tipo\":\"pago.aprobado\",\"referencia\":\"ALP-ABCDEFGHJK\"}";
        String firma = FirmaHmac.firmar("secreto", cuerpo);
        assertThat(FirmaHmac.verificar("secreto", cuerpo, firma)).isTrue();
        assertThat(FirmaHmac.verificar("secreto", cuerpo, firma.toUpperCase())).isTrue();
        assertThat(FirmaHmac.verificar("secreto", cuerpo + " ", firma)).isFalse();
        assertThat(FirmaHmac.verificar("otro", cuerpo, firma)).isFalse();
        assertThat(FirmaHmac.verificar("secreto", cuerpo, null)).isFalse();
        assertThat(FirmaHmac.verificar("", cuerpo, firma)).isFalse();
    }

    @Test
    void manifiestoDeMercadoPago() {
        String v1 = FirmaHmac.firmar("mp", "id:abc123;request-id:req-9;ts:1731963000;");
        assertThat(FirmasMercadoPago.valida("mp", "ts=1731963000,v1=" + v1, "ABC123", "req-9")).isTrue();
        assertThat(FirmasMercadoPago.valida("mp", "ts=1731963001,v1=" + v1, "ABC123", "req-9")).isFalse();
        assertThat(FirmasMercadoPago.valida("mp", "v1=" + v1, "ABC123", "req-9")).isFalse();
        assertThat(FirmasMercadoPago.valida("mp", null, "ABC123", "req-9")).isFalse();
        String sinSolicitud = FirmaHmac.firmar("mp", "id:55;ts:1;");
        assertThat(FirmasMercadoPago.valida("mp", "ts=1, v1=" + sinSolicitud, "55", null)).isTrue();
    }
}
