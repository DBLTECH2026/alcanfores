package pe.alcanfores.catalogo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subida de fotos: bytes mágicos, tamaño, foto principal única, orden y borrado del archivo. */
class FotoAdminTest extends PruebaIntegracion {

    static final Path CARPETA = Path.of("target/uploads-prueba/canchas");

    long nuevaCancha() throws Exception {
        String nombre = "Fotos " + UUID.randomUUID().toString().substring(0, 8);
        String json = mvc.perform(post("/api/admin/canchas").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(CanchaAdminTest.cuerpo(nombre, "40", "50")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    MockMultipartHttpServletRequestBuilder subida(long idCancha, String nombre, byte[] bytes) {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/admin/canchas/{id}/fotos", idCancha);
        b.file(new MockMultipartFile("archivo", nombre, "image/jpeg", bytes));
        b.header("Authorization", tokenAdmin());
        return b;
    }

    String subir(long idCancha, byte[] bytes) throws Exception {
        return mvc.perform(subida(idCancha, "foto.png", bytes)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void laPrimeraFotoEsPrincipalYSeGuardaConNombreUuid() throws Exception {
        long id = nuevaCancha();
        String json = mvc.perform(subida(id, "mi foto.png", ImagenesDePrueba.png(320, 240)).param("leyenda", "  Vista   general "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(true))
                .andExpect(jsonPath("$.ancho").value(320))
                .andExpect(jsonPath("$.alto").value(240))
                .andExpect(jsonPath("$.leyenda").value("Vista general"))
                .andExpect(jsonPath("$.url", startsWith("/api/catalogo/fotos/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(json, "$.url");
        String archivo = url.substring(url.lastIndexOf('/') + 1);
        assertThat(archivo).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(CARPETA.resolve(archivo))).isTrue();

        mvc.perform(subida(id, "segunda.jpg", ImagenesDePrueba.jpg(400, 300)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(false))
                .andExpect(jsonPath("$.orden").value(2));
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void archivoConExtensionCambiadaSeRechazaPorSusBytes() throws Exception {
        long id = nuevaCancha();
        byte[] texto = "esto no es una imagen, aunque se llame foto.jpg".getBytes(StandardCharsets.UTF_8);
        mvc.perform(subida(id, "foto.jpg", texto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Sube una imagen JPG, PNG o WebP de hasta 5 MB."));
        // Cabecera de JPG pegada a basura: los bytes mágicos engañan pero el lector de imágenes no.
        byte[] disfrazado = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};
        mvc.perform(subida(id, "foto.jpg", disfrazado)).andExpect(status().isBadRequest());
        mvc.perform(subida(id, "vacia.jpg", new byte[0])).andExpect(status().isBadRequest());
    }

    @Test
    void imagenMuyPequeniaSeRechaza() throws Exception {
        long id = nuevaCancha();
        mvc.perform(subida(id, "mini.png", ImagenesDePrueba.png(120, 90)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La foto debe medir al menos 200 px por lado."));
    }

    @Test
    void webpSeAceptaPorSuFirmaYSinDimensiones() throws Exception {
        long id = nuevaCancha();
        mvc.perform(subida(id, "foto.webp", ImagenesDePrueba.webp()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", org.hamcrest.Matchers.endsWith(".webp")))
                .andExpect(jsonPath("$.ancho", nullValue()));
    }

    @Test
    void leyendaLargaYFaltaDeArchivoDan400() throws Exception {
        long id = nuevaCancha();
        mvc.perform(subida(id, "a.png", ImagenesDePrueba.png(300, 300)).param("leyenda", "x".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("leyenda"));
        mvc.perform(multipart("/api/admin/canchas/{id}/fotos", id).header("Authorization", tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("archivo"));
    }

    @Test
    void recepcionNoPuedeSubirFotos() throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/admin/canchas/1/fotos");
        b.file(new MockMultipartFile("archivo", "a.png", "image/png", ImagenesDePrueba.png(300, 300)));
        mvc.perform(b.header("Authorization", tokenRecepcion())).andExpect(status().isForbidden());
    }

    @Test
    void cambiarLaPrincipalDejaSoloUnaYNoSePuedeQuedarSinPrincipal() throws Exception {
        long id = nuevaCancha();
        long primera = ((Number) JsonPath.read(subir(id, ImagenesDePrueba.png(300, 300)), "$.id")).longValue();
        long segunda = ((Number) JsonPath.read(subir(id, ImagenesDePrueba.png(300, 300)), "$.id")).longValue();

        mvc.perform(patch("/api/admin/canchas/{id}/fotos/{f}", id, primera).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"principal\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FOTO_PRINCIPAL_REQUERIDA"));

        mvc.perform(patch("/api/admin/canchas/{id}/fotos/{f}", id, segunda).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"principal\": true, \"orden\": 1, \"leyenda\": \"Portada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true))
                .andExpect(jsonPath("$.leyenda").value("Portada"));

        mvc.perform(get("/api/admin/canchas/{id}", id).header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.fotos[?(@.principal == true)].id").value(org.hamcrest.Matchers.contains((int) segunda)))
                .andExpect(jsonPath("$.fotoPrincipal").value(org.hamcrest.Matchers.startsWith("/api/catalogo/fotos/")));

        mvc.perform(patch("/api/admin/canchas/{id}/fotos/{f}", id, segunda).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/canchas/{id}/fotos/{f}", id, segunda).header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orden\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("orden"));
    }

    @Test
    void borrarLaPrincipalPromueveLaSiguienteYBorraElArchivo() throws Exception {
        long id = nuevaCancha();
        String json = subir(id, ImagenesDePrueba.png(300, 300));
        long primera = ((Number) JsonPath.read(json, "$.id")).longValue();
        String url = JsonPath.read(json, "$.url");
        long segunda = ((Number) JsonPath.read(subir(id, ImagenesDePrueba.png(300, 300)), "$.id")).longValue();

        mvc.perform(delete("/api/admin/canchas/{id}/fotos/{f}", id, primera).header("Authorization", tokenAdmin()))
                .andExpect(status().isNoContent());
        assertThat(Files.exists(CARPETA.resolve(url.substring(url.lastIndexOf('/') + 1)))).isFalse();
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/canchas/{id}", id).header("Authorization", tokenAdmin()))
                .andExpect(jsonPath("$.fotos[0].id").value((int) segunda))
                .andExpect(jsonPath("$.fotos[0].principal").value(true));
        mvc.perform(delete("/api/admin/canchas/{id}/fotos/{f}", id, primera).header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
        // La foto de otra cancha no se puede borrar desde esta.
        mvc.perform(delete("/api/admin/canchas/1/fotos/{f}", segunda).header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void maximoDiezFotosPorCancha() throws Exception {
        long id = nuevaCancha();
        byte[] img = ImagenesDePrueba.png(200, 200);
        for (int i = 0; i < 10; i++) {
            subir(id, img);
        }
        mvc.perform(subida(id, "once.png", img))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_FOTOS"));
    }

    @Test
    void subirAUnaCanchaInexistenteDa404() throws Exception {
        mvc.perform(subida(99999, "a.png", ImagenesDePrueba.png(300, 300))).andExpect(status().isNotFound());
    }
}
