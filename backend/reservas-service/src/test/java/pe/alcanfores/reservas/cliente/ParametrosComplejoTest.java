package pe.alcanfores.reservas.cliente;

import org.junit.jupiter.api.Test;
import pe.alcanfores.reservas.modelo.Franja;

import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParametrosComplejoTest {

    @Test
    void porDefectoHayQuinceTurnosDeOchoADiezDeLaNoche() {
        ParametrosComplejo p = ParametrosComplejo.porDefecto();
        assertThat(p.horasDeInicio()).hasSize(15).startsWith(LocalTime.of(8, 0)).endsWith(LocalTime.of(22, 0));
        assertThat(p.franja(LocalTime.of(17, 0))).isEqualTo(Franja.DIA);
        assertThat(p.franja(LocalTime.of(18, 0))).isEqualTo(Franja.NOCHE);
    }

    @Test
    void leeClavesSnakeOCamelYDescartaValoresInvalidos() {
        ParametrosComplejo p = ParametrosComplejo.desde(Map.of("adelanto_pct", 30, "diasVisibles", "7", "duracion_min", "90",
                "inicio_noche", "19:00", "ultimo_turno", "00:00", "minutos_para_pagar", "abc", "devolucion_horas", "-5",
                "direccion", "  "));
        assertThat(p.adelantoPct()).isEqualTo(30);
        assertThat(p.diasVisibles()).isEqualTo(7);
        assertThat(p.minutosParaPagar()).isEqualTo(20);
        assertThat(p.devolucionHoras()).isEqualTo(24);
        assertThat(p.direccion()).contains("Carretera Central");
        assertThat(p.horasDeInicio()).hasSize(10).endsWith(LocalTime.of(21, 30));
        assertThat(p.franja(LocalTime.of(18, 30))).isEqualTo(Franja.DIA);
        assertThat(ParametrosComplejo.desde(null)).isEqualTo(ParametrosComplejo.porDefecto());
    }

    @Test
    void leeLaVentanaDeAsistenciaYSinRecordatorioHorasNoFalla() {
        assertThat(ParametrosComplejo.porDefecto().asistenciaDesdeMin()).isEqualTo(30);
        assertThat(ParametrosComplejo.desde(Map.of("asistencia_desde_min", "45", "recordatorio_minutos", "120,15"))
                .asistenciaDesdeMin()).isEqualTo(45);
        assertThat(ParametrosComplejo.desde(Map.of("asistencia_desde_min", "0")).asistenciaDesdeMin()).isZero();
        assertThat(ParametrosComplejo.desde(Map.of("asistencia_desde_min", "181")).asistenciaDesdeMin()).isEqualTo(30);
        assertThat(ParametrosComplejo.desde(Map.of("asistencia_desde_min", "media hora")).asistenciaDesdeMin()).isEqualTo(30);
    }

    @Test
    void lasCanchasSemillaTienenLosPreciosDelSpec() {
        assertThat(CanchaCatalogo.semilla()).hasSize(4);
        CanchaCatalogo c3 = CanchaCatalogo.semilla().get(2);
        assertThat(c3.precio(Franja.DIA)).isEqualByComparingTo("90.00");
        assertThat(c3.precio(Franja.NOCHE)).isEqualByComparingTo("120.00");
        assertThat(new CanchaCatalogo(9L, "X", "VOLEY", c3.precioDia(), c3.precioNoche(), "INACTIVA").activa()).isFalse();
    }
}
