package pe.alcanfores.pagos.servicio;

import org.junit.jupiter.api.Test;
import pe.alcanfores.pagos.cliente.CatalogoCliente;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguracionComercioTest {

    @Test
    void usaValoresPorDefectoSiCatalogoNoResponde() {
        CatalogoCliente catalogo = mock(CatalogoCliente.class);
        when(catalogo.configuracion()).thenReturn(Map.of());
        ConfiguracionComercio c = new ConfiguracionComercio(catalogo);
        assertThat(c.emisor().ruc()).isEqualTo("20000000001");
        assertThat(c.emisor().razonSocial()).isEqualTo("Los Alcanfores Sport E.I.R.L. (ficticia)");
        assertThat(c.adelantoPct()).isEqualTo(50);
        assertThat(c.yapeTitular()).isEqualTo("Complejo Deportivo Los Alcanfores");
    }

    @Test
    void tomaLaConfiguracionDelCatalogoConCacheYDescartaValoresInvalidos() {
        CatalogoCliente catalogo = mock(CatalogoCliente.class);
        when(catalogo.configuracion()).thenReturn(Map.of("yape_numero", "912345678", "adelanto_pct", "300",
                "ruc_emisor", "12345678901", "plin_numero", "", "nombre_complejo", "Complejo Los Alcanfores"));
        ConfiguracionComercio c = new ConfiguracionComercio(catalogo);
        assertThat(c.yapeNumero()).isEqualTo("912345678");
        assertThat(c.plinNumero()).isEqualTo("914995187");
        assertThat(c.adelantoPct()).isEqualTo(50);
        assertThat(c.emisor().ruc()).isEqualTo("20000000001");
        assertThat(c.nombreComplejo()).isEqualTo("Complejo Los Alcanfores");
        verify(catalogo, times(1)).configuracion();
    }

    @Test
    void porcentajeNoNumerico() {
        CatalogoCliente catalogo = mock(CatalogoCliente.class);
        when(catalogo.configuracion()).thenReturn(Map.of("adelanto_pct", "cincuenta"));
        assertThat(new ConfiguracionComercio(catalogo).adelantoPct()).isEqualTo(50);
    }
}
