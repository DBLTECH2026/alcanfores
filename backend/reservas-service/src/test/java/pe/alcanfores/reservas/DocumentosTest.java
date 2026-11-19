package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.alcanfores.reservas.cliente.ProveedorDocumentos;
import pe.alcanfores.reservas.dto.DocumentoDtos.DniRespuesta;
import pe.alcanfores.reservas.dto.DocumentoDtos.RucRespuesta;
import pe.alcanfores.reservas.servicio.DocumentoServicio;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/documentos: validación previa, 503 sin proveedor, 404 si no existe y caché de 30 días. */
class DocumentosTest extends PruebaIntegracion {

    @MockitoBean
    private ProveedorDocumentos proveedor;
    @Autowired
    private DocumentoServicio servicio;

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
        when(proveedor.consultarRuc("20612345679")).thenReturn(Optional.of(
                new RucRespuesta("20612345679", "LOS ALCANFORES SPORT E.I.R.L.", "Car. Central km 11.5", "ACTIVO", "HABIDO",
                        "SUNAT (Factiliza)")));
        when(proveedor.consultarDni("70123321")).thenReturn(Optional.of(
                new DniRespuesta("70123321", "Diego Armando", "Ramos", "Quispe", "Ramos Quispe", "RENIEC (Factiliza)")));
        assertThat(servicio.consultarRuc("20612345679").razonSocial()).contains("ALCANFORES");
        servicio.consultarDni("70123321");
        reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321");
        assertThat(jdbc.queryForObject("SELECT verificado_reniec FROM cliente WHERE dni = '70123321'", Boolean.class)).isTrue();
    }
}
