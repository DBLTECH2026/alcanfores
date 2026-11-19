package pe.alcanfores.notificaciones.controlador;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.PruebaIntegracion;
import pe.alcanfores.notificaciones.modelo.Adjunto;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.EstadoNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.servicio.NotificacionServicio;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /interno/codigos}: modo simulado, WhatsApp, aviso de contraseña cambiada, validación y sin reintentos. */
@AutoConfigureMockMvc
class CodigosInternosTest extends PruebaIntegracion {

    private static final String TOKEN = "token-interno-solo-para-desarrollo-local";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NotificacionServicio bandeja;

    private ResultActions enviar(String cuerpo, String token) throws Exception {
        return mvc.perform(post("/interno/codigos").header("X-Interno-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("WhatsApp sin Evolution: SIMULADO, número 51…, tarjeta codigo-verificacion y el código enmascarado")
    void whatsappSimulado() throws Exception {
        enviar("{\"canal\":\"WHATSAPP\",\"destino\":\"987 654 321\",\"nombre\":\"Lucía Mendoza\",\"codigo\":\"007311\","
                + "\"motivo\":\"RECUPERAR_CONTRASENA\"}", TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIMULADO"))
                .andExpect(jsonPath("$.simulado").value(true));

        Notificacion n = notificaciones.findAll().get(0);
        assertThat(n.getCanal()).isEqualTo(CanalNotificacion.WHATSAPP);
        assertThat(n.getDestino()).isEqualTo("51987654321");
        assertThat(n.getCodigoReserva()).isNull();
        assertThat(n.getCuerpo()).isEqualTo("*Código de verificación*\n\nTu código para cambiar tu contraseña de Alcanfores "
                + "es ********. Vence en 10 minutos. Si no fuiste tú, ignora este mensaje.\n\nComplejo Deportivo Los Alcanfores");
        assertThat(n.getAdjuntos()).extracting(Adjunto::referencia).containsExactly("codigo-verificacion");
    }

    @Test
    @DisplayName("Aviso «Tu contraseña se cambió» por correo (sin código), con tarjeta contrasena-cambiada")
    void contrasenaCambiada() throws Exception {
        enviar("{\"canal\":\"CORREO\",\"destino\":\"lucia@correo.pe\",\"nombre\":\"Lucía\",\"motivo\":\"CONTRASENA_CAMBIADA\"}",
                TOKEN).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SIMULADO"));

        Notificacion n = notificaciones.findAll().get(0);
        assertThat(n.getEvento()).isEqualTo("cuenta.contrasena");
        assertThat(n.getAsunto()).isEqualTo("Tu contraseña se cambió");
        assertThat(n.getCuerpo()).contains("Hola, Lucía", "se cambió el", "cid:tarjeta-aviso",
                "se usó tu cuenta en el sistema de reservas");
        assertThat(n.getAdjuntos()).extracting(Adjunto::referencia).containsExactly("contrasena-cambiada");
    }

    @Test
    @DisplayName("Validación: canal, motivo, código de 6 dígitos y destino según el canal; sin token interno → 403")
    void validacion() throws Exception {
        enviar("{\"destino\":\"\",\"codigo\":\"12\"}", TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems("canal", "destino", "codigo", "motivo")));
        enviar("{\"canal\":\"CORREO\",\"destino\":\"a@b.pe\",\"motivo\":\"RECUPERAR_CONTRASENA\"}", TOKEN)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("codigo"));
        enviar("{\"canal\":\"WHATSAPP\",\"destino\":\"a@b.pe\",\"codigo\":\"123456\",\"motivo\":\"RECUPERAR_CONTRASENA\"}", TOKEN)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("destino"));
        enviar("{\"canal\":\"CORREO\",\"destino\":\"987654321\",\"codigo\":\"123456\",\"motivo\":\"RECUPERAR_CONTRASENA\"}", TOKEN)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("destino"));
        enviar("{\"canal\":\"CORREO\",\"destino\":\"a@b.pe\",\"codigo\":\"123456\",\"motivo\":\"RECUPERAR_CONTRASENA\"}", "otro")
                .andExpect(status().isForbidden());
        assertThat(notificaciones.count()).isZero();
    }

    @Test
    @DisplayName("Un código FALLIDO no se reintenta solo ni a mano (se guardó enmascarado)")
    void codigoNoSeReintenta() {
        Notificacion n = new Notificacion(null, "cuenta.codigo", CanalNotificacion.CORREO, "a@b.pe", "Tu código", "******",
                List.of(), Tiempo.ahora().minusMinutes(10));
        n.registrarResultado(EstadoNotificacion.FALLIDO, "caído", Tiempo.ahora());
        Long id = notificaciones.save(n).getId();

        assertThat(bandeja.reintentarFallidas()).isZero();
        assertThatThrownBy(() -> bandeja.reintentar(id)).isInstanceOf(NegocioExcepcion.class)
                .hasMessageContaining("el cliente debe pedir un código nuevo");
        assertThat(bandeja.detalle(id).reintentable()).isFalse();
    }
}
