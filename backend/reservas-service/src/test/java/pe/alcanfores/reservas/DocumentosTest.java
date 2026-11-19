package pe.alcanfores.reservas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.reservas.cliente.FactilizaProveedor;
import pe.alcanfores.reservas.cliente.ProveedorDocumentos;
import pe.alcanfores.reservas.config.ReservasPropiedades;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;
import pe.alcanfores.reservas.repositorio.ConsultaDocumentoRepositorio;
import pe.alcanfores.reservas.servicio.DocumentoServicio;
import pe.alcanfores.reservas.servicio.impl.DocumentoServicioImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/documentos: validación previa, 503 sin proveedor, 404 si no existe (en caché 1 día) y caché de 30 días. */
class DocumentosTest extends PruebaIntegracion {

    @MockitoBean
    private ProveedorDocumentos proveedor;
    @Autowired
    private DocumentoServicio servicio;
    @Autowired
    private ConsultaDocumentoRepositorio cache;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void sinTokenDeFactilizaResponde503() throws Exception {
        when(proveedor.disponible()).thenReturn(false);
        mvc.perform(get("/api/documentos/dni/70123321"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("CONSULTA_NO_DISPONIBLE"));
    }

    @Test
    void validaElDniYElRucAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/documentos/dni/1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dni"))
                .andExpect(jsonPath("$.mensaje").value("El DNI debe tener 8 dígitos."));
        mvc.perform(get("/api/documentos/ruc/20612345678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El RUC no es válido."));
        verify(proveedor, never()).consultarDni(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void dniInexistenteResponde404() throws Exception {
        when(proveedor.disponible()).thenReturn(true);
        when(proveedor.consultarDni("11111111")).thenReturn(Optional.empty());
        mvc.perform(get("/api/documentos/dni/11111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DNI_NO_ENCONTRADO"));
    }

    @Test
    void elNoEncontradoDeFactilizaSeGuardaUnDiaYNoGastaOtraConsulta() {
        // Cadena real: DocumentoServicioImpl -> FactilizaProveedor -> HTTP simulado con MockRestServiceServer.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer factiliza = MockRestServiceServer.bindTo(builder).build();
        String url = "https://api.factiliza.com/v1";
        DocumentoServicio real = new DocumentoServicioImpl(
                new FactilizaProveedor(builder, new ReservasPropiedades.Factiliza("token-prueba", url)), cache, mapper);
        factiliza.expect(once(), requestTo(url + "/dni/info/99010005"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"success\":false,\"message\":\"No se encontraron resultados\"}"));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> real.consultarDni("99010005"))
                    .isInstanceOfSatisfying(NegocioExcepcion.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(404);
                        assertThat(e.getCodigo()).isEqualTo("DNI_NO_ENCONTRADO");
                        assertThat(e.getMessage()).contains("No encontramos ese DNI en RENIEC", "a mano");
                    });
        }
        factiliza.verify();
        assertThat(jdbc.queryForObject("SELECT fuente FROM consulta_documento WHERE tipo = 'DNI' AND numero = '99010005'",
                String.class)).isEqualTo("NO_ENCONTRADO");

        // Pasado un día se vuelve a preguntar; si ahora existe, se guarda y se responde.
        fijarReloj(AHORA.plusDays(1).plusMinutes(1));
        factiliza.reset();
        factiliza.expect(once(), requestTo(url + "/dni/info/99010005")).andRespond(withSuccess("""
                {"success":true,"data":{"numero":"99010005","nombres":"LUCIA","apellido_paterno":"MENDOZA",
                 "apellido_materno":"PAREDES"}}
                """, MediaType.APPLICATION_JSON));
        assertThat(real.consultarDni("99010005").apellidos()).isEqualTo("Mendoza Paredes");
        factiliza.verify();
    }

    @Test
    void rucInexistenteResponde404YQuedaEnCache() throws Exception {
        when(proveedor.disponible()).thenReturn(true);
        when(proveedor.consultarRuc("20000000001")).thenReturn(Optional.empty());
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/documentos/ruc/20000000001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("RUC_NO_ENCONTRADO"))
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("razón social")));
        }
        verify(proveedor, times(1)).consultarRuc("20000000001");
    }

    @Test
    void unDniNoEncontradoNoMarcaAlClienteComoVerificado() throws Exception {
        when(proveedor.disponible()).thenReturn(true);
        when(proveedor.consultarDni("70123321")).thenReturn(Optional.empty());
        mvc.perform(get("/api/documentos/dni/70123321")).andExpect(status().isNotFound());
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321");
        assertThat(jdbc.queryForObject("SELECT verificado_reniec FROM cliente WHERE dni = '70123321'", Boolean.class)).isFalse();
    }

    @Test
    void laSegundaConsultaSaleDeLaCache() throws Exception {
        when(proveedor.disponible()).thenReturn(true);
        when(proveedor.consultarDni("70123321")).thenReturn(Optional.of(
                new DniRespuesta("70123321", "Rosa", "Huamán", "Quispe", "Huamán Quispe", "RENIEC (Factiliza)")));
        mvc.perform(get("/api/documentos/dni/70123321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Rosa"))
                .andExpect(jsonPath("$.fuente").value("RENIEC (Factiliza)"));
        mvc.perform(get("/api/documentos/dni/70123321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellidos").value("Huamán Quispe"))
                .andExpect(jsonPath("$.fuente").value("CACHE"));
        verify(proveedor, times(1)).consultarDni("70123321");

        // Pasados 30 días se vuelve a consultar al proveedor.
        fijarReloj(AHORA.plusDays(31));
        servicio.consultarDni("70123321");
        verify(proveedor, times(2)).consultarDni("70123321");
    }

    @Test
    void elClienteConDniConsultadoQuedaVerificado() throws Exception {
        when(proveedor.disponible()).thenReturn(true);
        when(proveedor.consultarRuc("20000000001")).thenReturn(Optional.of(
                new RucRespuesta("20000000001", "LOS ALCANFORES SPORT E.I.R.L.", "Car. Central km 11.5", "ACTIVO", "HABIDO",
                        "SUNAT (Factiliza)")));
        when(proveedor.consultarDni("70123321")).thenReturn(Optional.of(
                new DniRespuesta("70123321", "Diego Armando", "Ramos", "Quispe", "Ramos Quispe", "RENIEC (Factiliza)")));
        assertThat(servicio.consultarRuc("20000000001").razonSocial()).contains("ALCANFORES");
        servicio.consultarDni("70123321");
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321");
        assertThat(jdbc.queryForObject("SELECT verificado_reniec FROM cliente WHERE dni = '70123321'", Boolean.class)).isTrue();
    }
}
