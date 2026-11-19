package pe.alcanfores.catalogo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Portal público: complejo, canchas de la semilla, detalle por id o slug, reseñas y fotos. */
class CatalogoPublicoTest extends PruebaIntegracion {

    @Test
    void complejoConHorarioReglasYPagos() throws Exception {
        mvc.perform(get("/api/catalogo/complejo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Complejo Deportivo Los Alcanfores"))
                .andExpect(jsonPath("$.whatsapp").value("914995187"))
                .andExpect(jsonPath("$.horario.primerTurno").value("08:00"))
                .andExpect(jsonPath("$.horario.duracionMin").value(60))
                .andExpect(jsonPath("$.horario.diasVisibles").value(14))
                .andExpect(jsonPath("$.reglas.adelantoPct").value(50))
                .andExpect(jsonPath("$.reglas.minutosParaPagar").value(20))
                .andExpect(jsonPath("$.pagos.yapeNumero").value("900111222"))
                .andExpect(jsonPath("$.latitud").value(-12.0196));
    }

    @Test
    void listaLasCanchasActivasConFotoYCalificacion() throws Exception {
        mvc.perform(get("/api/catalogo/canchas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("cancha-1-futbol-5"))
                .andExpect(jsonPath("$[0].deporte").value("FUTBOL_5"))
                .andExpect(jsonPath("$[0].deporteTexto").value("Fútbol 5"))
                .andExpect(jsonPath("$[0].precioDia").value(60.00))
                .andExpect(jsonPath("$[0].precioNoche").value(80.00))
                .andExpect(jsonPath("$[0].fotoPrincipal", startsWith("/api/catalogo/fotos/")))
                .andExpect(jsonPath("$[0].calificacion.total").value(5))
                .andExpect(jsonPath("$[0].calificacion.promedio").value(4.4));
    }

    @Test
    void detallePorSlugYPorIdConFotosYResumen() throws Exception {
        mvc.perform(get("/api/catalogo/canchas/losa-multiusos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.deporte").value("MULTIUSO"))
                .andExpect(jsonPath("$.caracteristicas", hasSize(7)))
                .andExpect(jsonPath("$.fotos", hasSize(3)))
                .andExpect(jsonPath("$.fotos[0].principal").value(true))
                .andExpect(jsonPath("$.fotos[0].leyenda").value("Losa multiusos con red de vóley y tableros de básquet"))
                .andExpect(jsonPath("$.fotos[0].ancho").value(1600))
                .andExpect(jsonPath("$.resumenResenas.total").value(4))
                .andExpect(jsonPath("$.resumenResenas.promedio").value(4.3))
                .andExpect(jsonPath("$.resumenResenas.distribucion.5").value(2))
                .andExpect(jsonPath("$.resumenResenas.distribucion.4").value(1))
                .andExpect(jsonPath("$.resumenResenas.distribucion.3").value(1))
                .andExpect(jsonPath("$.resumenResenas.distribucion.1").value(0));

        mvc.perform(get("/api/catalogo/canchas/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("cancha-3-futbol-7"))
                .andExpect(jsonPath("$.descripcionLarga", startsWith("La Cancha 3")));
    }

    @Test
    void canchaInexistenteDa404ConMensaje() throws Exception {
        mvc.perform(get("/api/catalogo/canchas/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No encontramos esa cancha."));
    }

    @Test
    void resenasOrdenadasPorMejorYPeor() throws Exception {
        mvc.perform(get("/api/catalogo/canchas/1/resenas").param("orden", "peor").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].puntuacion").value(3))
                .andExpect(jsonPath("$.contenido[0].verificada").value(true))
                .andExpect(jsonPath("$.contenido[0].respuesta", startsWith("Kevin")))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));

        mvc.perform(get("/api/catalogo/canchas/1/resenas").param("orden", "mejor"))
                .andExpect(jsonPath("$.contenido[0].puntuacion").value(5));

        mvc.perform(get("/api/catalogo/canchas/1/resenas"))
                .andExpect(jsonPath("$.contenido[0].autor").value("Erick Q."));
    }

    @Test
    void ordenOPaginacionInvalidosDan400() throws Exception {
        mvc.perform(get("/api/catalogo/canchas/1/resenas").param("orden", "aleatorio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("orden"));
        mvc.perform(get("/api/catalogo/canchas/1/resenas").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("size"));
    }

    @Test
    void sirveLaFotoConCacheDeSieteDias() throws Exception {
        String url = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/catalogo/canchas/1")).andReturn().getResponse().getContentAsString(), "$.fotoPrincipal");
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", is("max-age=604800, public")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void fotoConRutaManipuladaOInexistenteDa404() throws Exception {
        mvc.perform(get("/api/catalogo/fotos/..%2F..%2Fapplication.yml"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/catalogo/fotos/..secreto.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No encontramos esa foto."));
        mvc.perform(get("/api/catalogo/fotos/00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rutaTecnicaDeSaludEsPublica() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", endsWith("catalogo-service")));
    }
}
