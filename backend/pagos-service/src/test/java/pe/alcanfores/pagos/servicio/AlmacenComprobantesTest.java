package pe.alcanfores.pagos.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.pagos.config.PagosPropiedades;
import pe.alcanfores.pagos.dto.Mensajes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Imágenes por bytes mágicos, tamaño y dimensiones; nombres UUID y sin rutas del usuario. */
class AlmacenComprobantesTest {

    @TempDir
    Path carpeta;
    AlmacenComprobantes almacen;

    @BeforeEach
    void crear() {
        PagosPropiedades p = new PagosPropiedades();
        p.setUploadsDir(carpeta.toString());
        almacen = new AlmacenComprobantes(p);
    }

    static byte[] imagen(String formato, int ancho, int alto) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), formato, salida);
        return salida.toByteArray();
    }

    @Test
    void aceptaPngYJpgReales() throws IOException {
        assertThat(almacen.problema(imagen("png", 300, 600))).isEmpty();
        assertThat(almacen.problema(imagen("jpg", 200, 200))).isEmpty();
    }

    @Test
    void rechazaArchivosQueNoSonImagenesAunqueParezcan() throws IOException {
        assertThat(almacen.problema("hola, soy un .png falso".getBytes())).contains(Mensajes.IMAGEN);
        assertThat(almacen.problema(imagen("gif", 300, 300))).contains(Mensajes.IMAGEN);
        assertThat(almacen.problema(imagen("bmp", 300, 300))).contains(Mensajes.IMAGEN);
        assertThat(almacen.problema(null)).contains(Mensajes.IMAGEN);
        assertThat(almacen.problema(new byte[0])).contains(Mensajes.IMAGEN);
        // cabecera PNG válida pero contenido corrupto
        byte[] corrupto = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        assertThat(almacen.problema(corrupto)).contains(Mensajes.IMAGEN);
    }

    @Test
    void rechazaImagenesPequenasOMayoresA5Mb() throws IOException {
        assertThat(almacen.problema(imagen("png", 199, 800))).contains(Mensajes.IMAGEN_PEQUENA);
        byte[] grande = new byte[(int) AlmacenComprobantes.MAXIMO_BYTES + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;
        assertThat(almacen.problema(grande)).contains(Mensajes.IMAGEN);
    }

    @Test
    void guardaConNombreUuidYLeeSoloEsosNombres() throws IOException {
        String nombre = almacen.guardar(imagen("png", 300, 300));
        assertThat(nombre).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(carpeta.resolve("comprobantes").resolve(nombre))).isTrue();
        AlmacenComprobantes.Imagen leida = almacen.leer(nombre);
        assertThat(leida.tipoContenido()).isEqualTo("image/png");

        assertThatThrownBy(() -> almacen.leer("../../application.yml")).isInstanceOf(NegocioExcepcion.class);
        assertThatThrownBy(() -> almacen.leer(null)).isInstanceOf(NegocioExcepcion.class);
        assertThatThrownBy(() -> almacen.leer("00000000-0000-0000-0000-000000000000.png")).isInstanceOf(NegocioExcepcion.class);

        almacen.borrar(nombre);
        assertThat(Files.exists(carpeta.resolve("comprobantes").resolve(nombre))).isFalse();
        assertThatThrownBy(() -> almacen.guardar("texto".getBytes())).isInstanceOf(NegocioExcepcion.class);
    }
}
