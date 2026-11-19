package pe.alcanfores.catalogo.util;

import org.junit.jupiter.api.Test;
import pe.alcanfores.catalogo.ImagenesDePrueba;
import pe.alcanfores.catalogo.modelo.ListaTextoJson;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilidadesTest {

    @Test
    void detectaImagenesPorBytesMagicos() {
        assertThat(Imagenes.detectar(ImagenesDePrueba.png(10, 10))).contains(Imagenes.Tipo.PNG);
        assertThat(Imagenes.detectar(ImagenesDePrueba.jpg(10, 10))).contains(Imagenes.Tipo.JPG);
        assertThat(Imagenes.detectar(ImagenesDePrueba.webp())).contains(Imagenes.Tipo.WEBP);
        assertThat(Imagenes.detectar("GIF89a........".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(Imagenes.detectar("RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(Imagenes.detectar(new byte[]{1})).isEmpty();
        assertThat(Imagenes.detectar(null)).isEmpty();
    }

    @Test
    void leeDimensionesSinDecodificarYNoLasInventaParaWebp() {
        assertThat(Imagenes.dimensiones(ImagenesDePrueba.png(321, 123))).contains(new Imagenes.Dimensiones(321, 123));
        assertThat(Imagenes.dimensiones(ImagenesDePrueba.webp())).isEmpty();
        assertThat(Imagenes.dimensiones(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEmpty();
    }

    @Test
    void tipoPorExtension() {
        assertThat(Imagenes.Tipo.porExtension("a.JPEG")).contains(Imagenes.Tipo.JPG);
        assertThat(Imagenes.Tipo.porExtension("a.webp").map(Imagenes.Tipo::mime)).contains("image/webp");
        assertThat(Imagenes.Tipo.porExtension("a.gif")).isEmpty();
    }

    @Test
    void slugsSinTildesNiSimbolos() {
        assertThat(Slugs.de("Cancha 1 · Fútbol 5")).isEqualTo("cancha-1-futbol-5");
        assertThat(Slugs.de("  Losa   multiusos  ")).isEqualTo("losa-multiusos");
        assertThat(Slugs.de("Cancha Ñandú")).isEqualTo("cancha-nandu");
        assertThat(Slugs.de("···")).isEqualTo("cancha");
        assertThat(Slugs.de("a".repeat(100))).hasSize(Slugs.MAXIMO);
    }

    @Test
    void promedioConUnDecimal() {
        assertThat(Calificaciones.promedio(22, 5)).isEqualByComparingTo("4.4");
        assertThat(Calificaciones.promedio(17, 4)).isEqualByComparingTo("4.3");
        assertThat(Calificaciones.promedio(0, 0)).isEqualByComparingTo("0.0");
    }

    @Test
    void listaDeTextosIdaYVueltaEnJson() {
        ListaTextoJson c = new ListaTextoJson();
        String json = c.convertToDatabaseColumn(List.of("Camerinos", "Estacionamiento \"techado\""));
        assertThat(json).isEqualTo("[\"Camerinos\",\"Estacionamiento \\\"techado\\\"\"]");
        assertThat(c.convertToEntityAttribute(json)).containsExactly("Camerinos", "Estacionamiento \"techado\"");
        assertThat(c.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThat(c.convertToEntityAttribute(null)).isEmpty();
        assertThatThrownBy(() -> c.convertToEntityAttribute("{no es json")).isInstanceOf(IllegalStateException.class);
    }
}
