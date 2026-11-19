package pe.alcanfores.auth.controlador;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.cliente.NotificacionesCliente;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solo con {@code RECUPERAR_DEVOLVER_CODIGO=true} (pruebas locales/E2E) la respuesta incluye {@code codigoPrueba}. */
@TestPropertySource(properties = "alcanfores.auth.recuperar.devolver-codigo=true")
class RecuperacionCodigoPruebaTest extends PruebaIntegracion {

    @MockitoBean
    private NotificacionesCliente notificaciones;

    @Test
    void devuelveElCodigoParaLaPruebaDePuntaAPunta() throws Exception {
        crearCuenta("70123456", "lucia@correo.pe", "Futbol2026");
        when(notificaciones.enviar(any())).thenReturn(new NotificacionesCliente.Resultado("ENVIADO", false, null));

        String r = mvc.perform(conJson(post("/api/auth/recuperar/enviar"),
                        Map.of("tipoCuenta", "CLIENTE", "usuario", "70123456", "canal", "CORREO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulado").value(false))
                .andExpect(jsonPath("$.codigoPrueba").isString())
                .andReturn().getResponse().getContentAsString();
        String codigo = json.readTree(r).get("codigoPrueba").asText();

        mvc.perform(conJson(post("/api/auth/recuperar/verificar"),
                        Map.of("tipoCuenta", "CLIENTE", "usuario", "lucia@correo.pe", "codigo", codigo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenRestablecer").isString());
    }
}
