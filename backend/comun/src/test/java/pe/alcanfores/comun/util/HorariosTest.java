package pe.alcanfores.comun.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.comun.util.Horarios.Tramo;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Textos de horario en 12 h (turnos seguidos, sueltos y medianoche). */
class HorariosTest {

    private static Tramo t(int desde, int hasta) {
        return new Tramo(LocalTime.of(desde, 0), hasta == 24 ? Horarios.FIN_DEL_DIA : LocalTime.of(hasta, 0));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "19:00; 21:00; 7:00–9:00 p. m.",
            "09:00; 10:00; 9:00–10:00 a. m.",
            "11:00; 13:00; 11:00 a. m.–1:00 p. m.",
            "12:00; 14:00; 12:00–2:00 p. m.",
            "08:00; 23:00; 8:00 a. m.–11:00 p. m.",
            "23:00; 23:59:59; 11:00 p. m.–12:00 a. m.",
            "07:00; 23:59:59; 7:00 a. m.–12:00 a. m.",
            "06:00; 00:00; 6:00 a. m.–12:00 a. m.",
            "07:30; 09:00; 7:30–9:00 a. m."})
    void rango(LocalTime desde, LocalTime hasta, String esperado) {
        assertThat(Horarios.rango(desde, hasta)).isEqualTo(esperado);
    }

    @Test
    void unTurnoODosSeguidosSonUnSoloTramo() {
        assertThat(Horarios.texto(List.of(t(19, 20)))).isEqualTo("7:00–8:00 p. m.");
        assertThat(Horarios.texto(List.of(t(20, 21), t(19, 20)))).isEqualTo("7:00–9:00 p. m.");
    }

    @Test
    void turnosNoSeguidosSeSeparanConComasEY() {
        assertThat(Horarios.texto(List.of(t(19, 20), t(9, 10)))).isEqualTo("9:00–10:00 a. m. y 7:00–8:00 p. m.");
        assertThat(Horarios.texto(List.of(t(8, 9), t(11, 12), t(12, 13), t(22, 23), t(23, 24))))
                .isEqualTo("8:00–9:00 a. m., 11:00 a. m.–1:00 p. m. y 10:00 p. m.–12:00 a. m.");
    }

    @Test
    void sinTurnosNoHayTexto() {
        assertThat(Horarios.texto(List.of())).isEmpty();
        assertThat(Horarios.texto(null)).isEmpty();
    }

    @Test
    void finDelDiaYMinutos() {
        assertThat(Horarios.esFinDelDia(Horarios.FIN_DEL_DIA)).isTrue();
        assertThat(Horarios.esFinDelDia(LocalTime.of(23, 0))).isFalse();
        assertThat(Horarios.esFinDelDia(null)).isFalse();
        assertThat(Horarios.minutos(Horarios.FIN_DEL_DIA)).isEqualTo(1440);
        assertThat(Horarios.minutos(LocalTime.of(7, 30))).isEqualTo(450);
        assertThat(Horarios.hora(1440)).isEqualTo(Horarios.FIN_DEL_DIA);
        assertThat(Horarios.hora(450)).isEqualTo(LocalTime.of(7, 30));
        assertThat(Tiempo.horaAmPm(Horarios.FIN_DEL_DIA)).isEqualTo("12:00 a. m.");
        assertThat(Tiempo.horaAmPm(LocalTime.MIDNIGHT)).isEqualTo("12:00 a. m.");
    }
}
