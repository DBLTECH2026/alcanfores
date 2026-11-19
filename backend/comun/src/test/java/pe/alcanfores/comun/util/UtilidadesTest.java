package pe.alcanfores.comun.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tiempo (zona Lima), Dinero (IGV y letras), Texto y PaginaRespuesta. */
class UtilidadesTest {

    @AfterEach
    void restablecerReloj() {
        Tiempo.restablecer();
    }

    // ---------- Tiempo ----------

    @Test
    void relojFijoEnHoraDeLima() {
        // 23:30 UTC = 18:30 en Lima (UTC-5, sin horario de verano)
        Tiempo.fijar(Clock.fixed(Instant.parse("2026-11-20T23:30:15.987Z"), Tiempo.ZONA));
        assertThat(Tiempo.ahora()).isEqualTo(LocalDateTime.of(2026, 11, 20, 18, 30, 15));
        assertThat(Tiempo.ahora().getNano()).isZero();
        assertThat(Tiempo.hoy()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(Tiempo.horaActual()).isEqualTo(LocalTime.of(18, 30, 15));
    }

    @Test
    void cercaDeMedianocheUtcSigueSiendoElDiaAnteriorEnLima() {
        Tiempo.fijar(Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), Tiempo.ZONA));
        assertThat(Tiempo.hoy()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void restablecerVuelveAlRelojReal() {
        Tiempo.fijar(Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), Tiempo.ZONA));
        Tiempo.restablecer();
        assertThat(Tiempo.hoy().getYear()).isGreaterThan(2000);
    }

    @Test
    void formatosDeFecha() {
        assertThat(Tiempo.fechaLarga(LocalDate.of(2026, 11, 20))).isEqualTo("viernes 20 de noviembre de 2026");
        assertThat(LocalDate.of(2026, 1, 5).format(Tiempo.FECHA_CORTA)).isEqualTo("05/01/2026");
        assertThat(LocalTime.of(7, 5).format(Tiempo.HORA)).isEqualTo("07:05");
        assertThat(LocalDateTime.of(2026, 11, 20, 19, 0).format(Tiempo.FECHA_HORA)).isEqualTo("20/11/2026 19:00");
    }

    @ParameterizedTest
    @CsvSource({
            "19:00, 7:00 p. m.",
            "00:00, 12:00 a. m.",
            "00:05, 12:05 a. m.",
            "12:00, 12:00 p. m.",
            "12:30, 12:30 p. m.",
            "09:07, 9:07 a. m.",
            "11:59, 11:59 a. m.",
            "23:45, 11:45 p. m."
    })
    void horaAmPm(String hora, String esperado) {
        assertThat(Tiempo.horaAmPm(LocalTime.parse(hora))).isEqualTo(esperado);
    }

    // ---------- Dinero ----------

    @ParameterizedTest
    @CsvSource({
            "1, UNO",
            "15, QUINCE",
            "20, VEINTE",
            "21, VEINTIUNO",
            "29, VEINTINUEVE",
            "30, TREINTA",
            "31, TREINTA Y UNO",
            "99, NOVENTA Y NUEVE",
            "101, CIENTO UNO",
            "200, DOSCIENTOS",
            "999, NOVECIENTOS NOVENTA Y NUEVE",
            "1000, MIL",
            "1001, MIL UNO",
            "2000, DOS MIL",
            "31000, TREINTA Y UN MIL",
            "100000, CIEN MIL",
            "101000, CIENTO UN MIL",
            "999999, NOVECIENTOS NOVENTA Y NUEVE MIL NOVECIENTOS NOVENTA Y NUEVE",
            "1000000, UN MILLON",
            "1000001, UN MILLON UNO",
            "2500000, DOS MILLONES QUINIENTOS MIL",
            "21000000, VEINTIUN MILLONES",
            "1000000000, MIL MILLONES"
    })
    void numerosEnLetras(long n, String esperado) {
        assertThat(Dinero.numeroEnLetras(n)).isEqualTo(esperado);
    }

    @Test
    void enLetrasRedondeaLosCentimos() {
        assertThat(Dinero.enLetras(new BigDecimal("0.005"))).isEqualTo("CERO CON 01/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("99.999"))).isEqualTo("CIEN CON 00/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("1.5"))).isEqualTo("UNO CON 50/100 SOLES");
        assertThat(Dinero.enLetras(new BigDecimal("1000000.01"))).isEqualTo("UN MILLON CON 01/100 SOLES");
    }

    @Test
    void redondeoYFormato() {
        assertThat(Dinero.redondear(new BigDecimal("2.345"))).isEqualByComparingTo("2.35");
        assertThat(Dinero.redondear(new BigDecimal("7")).scale()).isEqualTo(2);
        assertThat(Dinero.redondear(new BigDecimal("2.344"))).isEqualByComparingTo("2.34");
        assertThat(Dinero.formato(new BigDecimal("80"))).isEqualTo("S/ 80.00");
        assertThat(Dinero.formato(new BigDecimal("1234.5"))).isEqualTo("S/ 1234.50");
    }

    @ParameterizedTest
    @CsvSource({
            "80.00, 67.80, 12.20",
            "100.00, 84.75, 15.25",
            "118.00, 100.00, 18.00",
            "0.01, 0.01, 0.00",
            "45.50, 38.56, 6.94",
            "1.00, 0.85, 0.15"
    })
    void igvIncluidoSiempreCuadraConLaBase(String total, String base, String igv) {
        BigDecimal t = new BigDecimal(total);
        assertThat(Dinero.baseImponible(t)).isEqualByComparingTo(base);
        assertThat(Dinero.igvIncluido(t)).isEqualByComparingTo(igv);
        assertThat(Dinero.baseImponible(t).add(Dinero.igvIncluido(t))).isEqualByComparingTo(t);
        assertThat(Dinero.TASA_IGV).isEqualByComparingTo("0.18");
    }

    @Test
    void porcentajes() {
        assertThat(Dinero.porcentaje(new BigDecimal("90.00"), 50)).isEqualByComparingTo("45.00");
        assertThat(Dinero.porcentaje(new BigDecimal("85.00"), 30)).isEqualByComparingTo("25.50");
        assertThat(Dinero.porcentaje(new BigDecimal("33.33"), 50)).isEqualByComparingTo("16.67"); // 16.665 → 16.67
        assertThat(Dinero.porcentaje(new BigDecimal("80.00"), 0)).isEqualByComparingTo("0.00");
        assertThat(Dinero.porcentaje(new BigDecimal("80.00"), 100)).isEqualByComparingTo("80.00");
    }

    // ---------- Texto ----------

    @Test
    void limpiarYTitulo() {
        assertThat(Texto.limpiar(null)).isNull();
        assertThat(Texto.limpiar("  a   b \t c  ")).isEqualTo("a b c");
        assertThat(Texto.titulo(null)).isNull();
        assertThat(Texto.titulo("   ")).isEmpty();
        assertThat(Texto.titulo("maría-josé o'neill")).isEqualTo("María-José O'Neill");
        assertThat(Texto.titulo("ÑUÑEZ PEÑA")).isEqualTo("Ñuñez Peña");
    }

    @Test
    void celulares() {
        assertThat(Texto.normalizarCelular(null)).isNull();
        assertThat(Texto.normalizarCelular("+51 987 654 321")).isEqualTo("987654321");
        assertThat(Texto.normalizarCelular("51987654321")).isEqualTo("987654321");
        assertThat(Texto.normalizarCelular("(987) 654-321")).isEqualTo("987654321");
        assertThat(Texto.normalizarCelular("+52 987 654 321")).isEqualTo("52987654321"); // no se puede normalizar
        assertThat(Texto.celularWhatsapp("987 654 321")).isEqualTo("51987654321");
        assertThat(Texto.celularWhatsapp("+51 987654321")).isEqualTo("51987654321");
        assertThat(Texto.celularWhatsapp(null)).isNull();
    }

    @Test
    void correoYAutor() {
        assertThat(Texto.correo("  Rosa.Huaman@Correo.PE ")).isEqualTo("rosa.huaman@correo.pe");
        assertThat(Texto.correo(null)).isNull();
        assertThat(Texto.autorCorto("diego alonso", "ramos quispe")).isEqualTo("Diego R.");
        assertThat(Texto.autorCorto("ana", null)).isEqualTo("Ana");
        assertThat(Texto.autorCorto("ana", "   ")).isEqualTo("Ana");
        assertThat(Texto.autorCorto(null, null)).isEmpty();
        assertThat(Texto.autorCorto("luis", "ñahui")).isEqualTo("Luis Ñ.");
    }

    // ---------- PaginaRespuesta ----------

    @Test
    void paginaConMapeo() {
        Page<Integer> pagina = new PageImpl<>(List.of(4, 5, 6), PageRequest.of(1, 3), 10);
        PaginaRespuesta<String> r = PaginaRespuesta.de(pagina, n -> "n" + n);
        assertThat(r.contenido()).containsExactly("n4", "n5", "n6");
        assertThat(r.pagina()).isEqualTo(1);
        assertThat(r.tamanio()).isEqualTo(3);
        assertThat(r.totalElementos()).isEqualTo(10);
        assertThat(r.totalPaginas()).isEqualTo(4);
    }

    @Test
    void paginaSinMapeoYVacia() {
        PaginaRespuesta<String> r = PaginaRespuesta.de(new PageImpl<>(List.of("a", "b")));
        assertThat(r.contenido()).containsExactly("a", "b");
        assertThat(r.totalPaginas()).isEqualTo(1);

        PaginaRespuesta<String> vacia = PaginaRespuesta.de(Page.<String>empty(PageRequest.of(0, 20)));
        assertThat(vacia.contenido()).isEmpty();
        assertThat(vacia.tamanio()).isEqualTo(20);
        assertThat(vacia.totalElementos()).isZero();
        assertThat(vacia.totalPaginas()).isZero();
    }
}
