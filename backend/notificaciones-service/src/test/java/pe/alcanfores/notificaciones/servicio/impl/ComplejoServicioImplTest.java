package pe.alcanfores.notificaciones.servicio.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.Datos;
import pe.alcanfores.notificaciones.cliente.CatalogoCliente;
import pe.alcanfores.notificaciones.config.NotificacionesPropiedades;
import pe.alcanfores.notificaciones.servicio.DatosComplejo;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplejoServicioImplTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 19, 10, 0);
    private CatalogoCliente catalogo;
    private ComplejoServicioImpl servicio;

    @BeforeEach
    void preparar() {
        Datos.reloj(AHORA);
        catalogo = mock(CatalogoCliente.class);
        servicio = new ComplejoServicioImpl(catalogo, new NotificacionesPropiedades());
    }

    @AfterEach
    void restablecer() {
        Tiempo.restablecer();
    }

    @Test
    @DisplayName("Lee la configuración del catálogo y la guarda 5 minutos")
    void cache() {
        when(catalogo.configuracion()).thenReturn(Map.of("recordatorio_minutos", "15,120", "direccion", "Av. Nueva 123",
                "whatsapp", "+51 914 995 187", "devolucion_horas", "12"));

        DatosComplejo d = servicio.datos();
        servicio.datos();
        Datos.reloj(AHORA.plusMinutes(4));
        servicio.datos();

        assertThat(d.recordatorioMinutos()).containsExactly(120, 15);
        assertThat(d.devolucionHoras()).isEqualTo(12);
        assertThat(d.direccion()).isEqualTo("Av. Nueva 123");
        assertThat(d.referencia()).isEqualTo("Paradero Caseta, a dos cuadras de la UTP Santa Clara");
        assertThat(d.whatsappTexto()).isEqualTo("914 995 187");
        assertThat(d.whatsappUrl()).isEqualTo("https://wa.me/51914995187");
        verify(catalogo, times(1)).configuracion();

        Datos.reloj(AHORA.plusMinutes(6));
        servicio.datos();
        verify(catalogo, times(2)).configuracion();
    }

    @Test
    @DisplayName("Si catálogo no responde usa los datos reales por defecto (120 min) y reintenta al minuto")
    void respaldo() {
        when(catalogo.configuracion()).thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "caído"));

        DatosComplejo d = servicio.datos();

        assertThat(d.nombre()).isEqualTo("Complejo Deportivo Los Alcanfores");
        assertThat(d.direccion()).isEqualTo("Carretera Central km 11.5, Santa Clara, Ate");
        assertThat(d.recordatorioMinutos()).containsExactly(120);
        assertThat(d.devolucionHoras()).isEqualTo(24);
        Datos.reloj(AHORA.plusSeconds(61));
        servicio.datos();
        verify(catalogo, times(2)).configuracion();
    }

    @Test
    @DisplayName("Al confirmar se relee el catálogo aunque la caché siga vigente; si no responde, se usa lo último")
    void actualizados() {
        when(catalogo.configuracion()).thenReturn(Map.of("recordatorio_minutos", "120"))
                .thenReturn(Map.of("recordatorio_minutos", "60,15"))
                .thenThrow(NegocioExcepcion.noDisponible("SERVICIO_NO_DISPONIBLE", "caído"));

        assertThat(servicio.datos().recordatorioMinutos()).containsExactly(120);
        assertThat(servicio.actualizados().recordatorioMinutos()).containsExactly(60, 15);
        assertThat(servicio.datos().recordatorioMinutos()).containsExactly(60, 15);
        assertThat(servicio.actualizados().recordatorioMinutos()).containsExactly(60, 15);
        verify(catalogo, times(3)).configuracion();
    }

    @Test
    @DisplayName("Valores fuera de rango o no numéricos se ignoran")
    void valoresInvalidos() {
        DatosComplejo d = servicio.combinar(Map.of("recordatorio_minutos", "cero", "devolucion_horas", "-5", "direccion", "  "));

        assertThat(d.recordatorioMinutos()).containsExactly(120);
        assertThat(d.devolucionHoras()).isEqualTo(24);
        assertThat(d.direccion()).isEqualTo("Carretera Central km 11.5, Santa Clara, Ate");
        for (String malo : new String[]{"4", "1441", "60,60", "10,20,30,40", "", "60;15"}) {
            assertThat(servicio.combinar(Map.of("recordatorio_minutos", malo)).recordatorioMinutos()).as(malo).containsExactly(120);
        }
        assertThat(servicio.combinar(Map.of("recordatorio_minutos", " 5 , 1440 ")).recordatorioMinutos()).containsExactly(1440, 5);
    }
}
