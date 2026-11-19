package pe.alcanfores.catalogo.servicio.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.catalogo.modelo.HorarioCancha;
import pe.alcanfores.comun.util.Horarios;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas del horario sin base de datos: textos de duración y de lo que sobra, y el horario por defecto. */
class HorarioServicioImplTest {

    @ParameterizedTest
    @CsvSource({"60, 1 h", "120, 2 h", "45, 45 min", "90, 1 h 30 min", "30, 30 min"})
    void duracionTexto(int minutos, String esperado) {
        assertThat(HorarioServicioImpl.duracionTexto(minutos)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"60, 1 hora", "120, 2 horas", "30, 30 minutos", "90, 1 hora y 30 minutos", "150, 2 horas y 30 minutos"})
    void sobraTexto(int minutos, String esperado) {
        assertThat(HorarioServicioImpl.sobraTexto(minutos)).isEqualTo(esperado);
    }

    @Test
    void problemaDelDia() {
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(8, 0), LocalTime.of(23, 0), 60, true)).isNull();
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(8, 0), LocalTime.of(23, 0), 120, true))
                .isEqualTo("Con turnos de 2 h, de 8:00 a. m. a 11:00 p. m. sobra 1 hora: ajusta el cierre a 10:00 p. m. o a 12:00 a. m.");
        // Cerca de la medianoche solo queda bajar el cierre.
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(8, 0), Horarios.FIN_DEL_DIA, 90, true))
                .isEqualTo("Con turnos de 1 h 30 min, de 8:00 a. m. a 12:00 a. m. sobra 1 hora: ajusta el cierre a 11:00 p. m.");
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(9, 0), LocalTime.of(9, 0), 60, true))
                .isEqualTo("El cierre debe ser posterior a la apertura.");
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(9, 0), LocalTime.of(10, 0), 50, false)).isNull();
        assertThat(HorarioServicioImpl.problemaDelDia(LocalTime.of(9, 0), LocalTime.of(10, 0), 90, true))
                .isEqualTo("Entre la apertura y el cierre no entra ni un turno de 1 h 30 min.");
    }

    @Test
    void semanaPorDefectoAjustaElCierreALaDuracion() {
        List<HorarioCancha> s = HorarioServicioImpl.semanaPorDefecto(LocalTime.of(8, 0), LocalTime.of(23, 0), 120);
        assertThat(s).hasSize(7).allMatch(HorarioCancha::isAbierto);
        assertThat(s.get(0).getCierre()).isEqualTo(LocalTime.of(22, 0));
        assertThat(HorarioServicioImpl.semanaPorDefecto(LocalTime.of(8, 0), LocalTime.of(23, 0), 60).get(6).getCierre())
                .isEqualTo(LocalTime.of(23, 0));
        assertThat(HorarioServicioImpl.semanaPorDefecto(LocalTime.of(22, 0), LocalTime.of(23, 0), 90).get(0).getCierre())
                .isEqualTo(LocalTime.of(23, 0));
    }
}
