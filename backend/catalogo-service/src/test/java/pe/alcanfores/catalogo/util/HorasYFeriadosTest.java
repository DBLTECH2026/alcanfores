package pe.alcanfores.catalogo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.catalogo.modelo.HorarioCancha;
import pe.alcanfores.catalogo.modelo.ListaIdsJson;
import pe.alcanfores.comun.util.Horarios;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pascua y feriados de Perú, horas «24:00», turnos de un día y el resumen de la semana. */
class HorasYFeriadosTest {

    @ParameterizedTest
    @CsvSource({"2024, 2024-03-31", "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28", "2028, 2028-04-16",
            "2038, 2038-04-25", "2008, 2008-03-23"})
    void pascua(int anio, LocalDate esperada) {
        assertThat(FeriadosPeru.pascua(anio)).isEqualTo(esperada);
    }

    @Test
    void feriadosDePeruDelAnio() {
        List<FeriadosPeru.Feriado> f = FeriadosPeru.del(2026);
        assertThat(f).hasSize(16);
        assertThat(f).extracting(FeriadosPeru.Feriado::fecha)
                .contains(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 6, 7),
                        LocalDate.of(2026, 7, 23), LocalDate.of(2026, 12, 9))
                .doesNotHaveDuplicates();
        assertThat(FeriadosPeru.del(2027)).extracting(FeriadosPeru.Feriado::fecha)
                .contains(LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 26));
    }

    @Test
    void formatoYLecturaDeHoras() {
        assertThat(Horas.formato(LocalTime.of(8, 0))).isEqualTo("08:00");
        assertThat(Horas.formato(Horarios.FIN_DEL_DIA)).isEqualTo("24:00");
        assertThat(Horas.formato(null)).isNull();
        assertThat(Horas.leer("24:00")).isEqualTo(Horarios.FIN_DEL_DIA);
        assertThat(Horas.leer("23:59")).isEqualTo(Horarios.FIN_DEL_DIA);
        assertThat(Horas.leer(" 07:30 ")).isEqualTo(LocalTime.of(7, 30));
        assertThat(Horas.leer(" ")).isNull();
        assertThat(Horas.leer(null)).isNull();
    }

    @Test
    void iniciosDeLosTurnos() {
        assertThat(Horas.inicios(LocalTime.of(8, 0), LocalTime.of(11, 0), 60)).containsExactly(480, 540, 600);
        assertThat(Horas.inicios(LocalTime.of(22, 0), Horarios.FIN_DEL_DIA, 60)).containsExactly(1320, 1380);
        assertThat(Horas.inicios(LocalTime.of(8, 0), LocalTime.of(9, 0), 90)).isEmpty();
        assertThat(Horas.inicios(null, LocalTime.of(9, 0), 60)).isEmpty();
        assertThat(Horas.inicios(LocalTime.of(8, 0), LocalTime.of(9, 0), 0)).isEmpty();
    }

    static List<HorarioCancha> semana(String... dias) {
        List<HorarioCancha> s = new ArrayList<>();
        for (int i = 0; i < dias.length; i++) {
            if ("cerrado".equals(dias[i])) {
                s.add(new HorarioCancha(i + 1, false, LocalTime.of(8, 0), LocalTime.of(23, 0)));
            } else {
                String[] p = dias[i].split("-");
                s.add(new HorarioCancha(i + 1, true, Horas.leer(p[0]), Horas.leer(p[1])));
            }
        }
        return s;
    }

    @Test
    void resumenDeLaSemana() {
        String n = "08:00-23:00";
        assertThat(Horas.resumen(semana(n, n, n, n, n, n, n))).isEqualTo("Todos los días 8:00 a. m.–11:00 p. m.");
        assertThat(Horas.resumen(semana(n, n, n, n, n, "07:00-23:00", "07:00-23:00")))
                .isEqualTo("Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–11:00 p. m.");
        assertThat(Horas.resumen(semana(n, "cerrado", n, n, n, n, "09:00-13:00")))
                .isEqualTo("Lun 8:00 a. m.–11:00 p. m. · Mar cerrado · Mié a sáb 8:00 a. m.–11:00 p. m. · Dom 9:00 a. m.–1:00 p. m.");
        assertThat(Horas.resumen(semana("cerrado", "cerrado", "cerrado", "cerrado", "cerrado", "cerrado", "cerrado")))
                .isEqualTo("Cerrada todos los días");
        assertThat(Horas.resumen(semana(n, n, n, n, n, n, "20:00-24:00")))
                .isEqualTo("Lun a sáb 8:00 a. m.–11:00 p. m. · Dom 8:00 p. m.–12:00 a. m.");
        assertThat(Horas.resumen(List.of())).isEqualTo("Sin horario");
        assertThat(Horas.resumen(null)).isEqualTo("Sin horario");
    }

    @Test
    void listaDeIdsEnJson() {
        ListaIdsJson c = new ListaIdsJson();
        assertThat(c.convertToDatabaseColumn(List.of(1L, 3L))).isEqualTo("[1,3]");
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute("[1, 3]")).containsExactly(1L, 3L);
        assertThat(c.convertToEntityAttribute(null)).isNull();
        assertThat(c.convertToEntityAttribute("null")).isNull();
        assertThatThrownBy(() -> c.convertToEntityAttribute("{no es json")).isInstanceOf(IllegalStateException.class);
    }
}
