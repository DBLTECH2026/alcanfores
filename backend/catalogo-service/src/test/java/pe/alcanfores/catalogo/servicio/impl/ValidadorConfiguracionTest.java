package pe.alcanfores.catalogo.servicio.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.alcanfores.catalogo.modelo.ClaveConfiguracion;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorConfiguracionTest {

    final ValidadorConfiguracion validador = new ValidadorConfiguracion();

    @ParameterizedTest(name = "{0} = «{1}» → {2}")
    @CsvSource(delimiter = '|', value = {
            "ADELANTO_PCT|0|0",
            "ADELANTO_PCT|100|100",
            "ADELANTO_PCT|007|7",
            "PRIMER_TURNO|08:00|08:00",
            "YAPE_NUMERO|+51 987-654-321|987654321",
            "CORREO|Informes@Alcanfores.PE|informes@alcanfores.pe",
            "LATITUD|-12.0196|-12.0196",
            "LONGITUD|-76.8865|-76.8865",
            "RUC_EMISOR|20000000001|20000000001",
            "LEMA|  Juega   cuando quieras |Juega cuando quieras",
            "MAPA_URL|https://maps.google.com/?q=1|https://maps.google.com/?q=1"
    })
    void valoresValidosSeNormalizan(ClaveConfiguracion clave, String valor, String esperado) {
        ValidadorConfiguracion.Resultado r = validador.validar(clave, valor);
        assertThat(r.valido()).isTrue();
        assertThat(r.valor()).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} = «{1}» inválido")
    @CsvSource(delimiter = '|', value = {
            "ADELANTO_PCT|101",
            "ADELANTO_PCT|-1",
            "ADELANTO_PCT|cincuenta",
            "DEVOLUCION_HORAS|0",
            "DURACION_MIN|20",
            "PRIMER_TURNO|24:00",
            "PRIMER_TURNO|8:00",
            "WHATSAPP|814995187",
            "CORREO|sin-arroba.pe",
            "LATITUD|-91",
            "LONGITUD|181.5",
            "RUC_EMISOR|20612345678",
            "MAPA_URL|http://inseguro.com",
            "MAPA_URL|https://sin punto",
            "NOMBRE_COMPLEJO|AB",
            "DIRECCION|<b>Ate</b>"
    })
    void valoresInvalidos(ClaveConfiguracion clave, String valor) {
        assertThat(validador.validar(clave, valor).valido()).isFalse();
    }

    @Test
    void vacioEsObligatorio() {
        assertThat(validador.validar(ClaveConfiguracion.LEMA, "   ").error()).isEqualTo("Este dato es obligatorio.");
        assertThat(validador.validar(ClaveConfiguracion.LEMA, null).valido()).isFalse();
    }

    static Map<String, String> horario(String primer, String ultimo, String noche, String duracion) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("primer_turno", primer);
        m.put("ultimo_turno", ultimo);
        m.put("inicio_noche", noche);
        m.put("duracion_min", duracion);
        m.put("dias_visibles", "14");
        m.put("dias_generados", "120");
        return m;
    }

    @Test
    void losDiasGeneradosNoPuedenSerMenosQueLosVisibles() {
        Map<String, String> c = horario("08:00", "23:00", "18:00", "60");
        c.put("dias_visibles", "30");
        c.put("dias_generados", "20");
        assertThat(validador.validarConjunto(c)).extracting(e -> e.campo()).containsExactly("dias_generados");
    }

    @Test
    void horarioCoherente() {
        assertThat(validador.validarConjunto(horario("08:00", "23:00", "18:00", "60"))).isEmpty();
    }

    @Test
    void horarioIncoherente() {
        assertThat(validador.validarConjunto(horario("22:00", "21:00", "18:00", "60")))
                .extracting(e -> e.campo()).containsExactly("ultimo_turno");
        assertThat(validador.validarConjunto(horario("08:00", "08:30", "08:15", "60")))
                .extracting(e -> e.campo()).containsExactly("duracion_min");
        assertThat(validador.validarConjunto(horario("08:00", "23:00", "23:30", "45")))
                .extracting(e -> e.campo()).containsExactly("inicio_noche");
        assertThat(validador.validarConjunto(horario("08:00", "23:00", "18:00", "50")))
                .extracting(e -> e.campo()).containsExactly("duracion_min");
    }
}
