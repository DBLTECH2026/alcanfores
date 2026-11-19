package pe.alcanfores.auth.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.evento.ConsumidorAuditoria;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Bitacora;
import pe.alcanfores.auth.modelo.Rol;
import pe.alcanfores.comun.eventos.AccionAuditadaEvento;
import pe.alcanfores.comun.util.Tiempo;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BitacoraIntegracionTest extends PruebaIntegracion {

    @Autowired
    private ConsumidorAuditoria consumidor;

    private Administrador rosa;

    @BeforeEach
    void datos() {
        rosa = crearUsuario("rhuaman", Rol.ADMIN);
    }

    private static AccionAuditadaEvento evento(String servicio, String actor, String accion, LocalDateTime cuando) {
        return new AccionAuditadaEvento(UUID.randomUUID(), cuando, servicio, 1L, actor, "ADMIN", accion, "pago", "15",
                "Reserva ALC-7K3M9P · S/ 40.00", "190.12.34.56");
    }

    @Test
    void elConsumidorEsIdempotente() {
        AccionAuditadaEvento e = evento("pagos-service", "rhuaman", "VALIDAR_PAGO", Tiempo.ahora());

        consumidor.alRecibirAccion(e);
        consumidor.alRecibirAccion(e);

        assertThat(bitacoras.count()).isEqualTo(1);
        assertThat(eventosProcesados.count()).isEqualTo(1);
        Bitacora b = bitacoras.findAll().get(0);
        assertThat(b.getServicio()).isEqualTo("pagos-service");
        assertThat(b.getAccion()).isEqualTo("VALIDAR_PAGO");
        assertThat(b.getIdEntidad()).isEqualTo("15");
        assertThat(b.getIp()).isEqualTo("190.12.34.56");
    }

    @Test
    void unEventoSinIdentificadorVaALaColaDeMuertos() {
        AccionAuditadaEvento sinId = new AccionAuditadaEvento(null, Tiempo.ahora(), "x", null, "y", null, "Z", null, null,
                null, null);
        assertThatThrownBy(() -> consumidor.alRecibirAccion(sinId)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(bitacoras.count()).isZero();
    }

    @Test
    void recortaTextosLargosAlTamanioDeLaColumna() {
        AccionAuditadaEvento e = new AccionAuditadaEvento(UUID.randomUUID(), Tiempo.ahora(), "reservas-service", null,
                null, null, "CANCELAR_RESERVA", "reserva", "ALC-AAAAAA", "x".repeat(800), null);
        consumidor.alRecibirAccion(e);

        Bitacora b = bitacoras.findAll().get(0);
        assertThat(b.getDetalle()).hasSize(500).endsWith("...");
        assertThat(b.getActor()).isEqualTo("Sistema");
    }

    @Test
    void buscaConFiltros() throws Exception {
        LocalDateTime ahora = Tiempo.ahora();
        consumidor.alRecibirAccion(evento("pagos-service", "rhuaman", "VALIDAR_PAGO", ahora));
        consumidor.alRecibirAccion(evento("pagos-service", "mtorres", "RECHAZAR_PAGO", ahora.minusDays(1)));
        consumidor.alRecibirAccion(evento("reservas-service", "mtorres", "CANCELAR_RESERVA", ahora.minusDays(10)));

        mvc.perform(get("/api/admin/bitacora").header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].accion").value("VALIDAR_PAGO"));

        mvc.perform(get("/api/admin/bitacora").param("servicio", "pagos-service").param("actor", "TORR")
                        .header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].accion").value("RECHAZAR_PAGO"));

        mvc.perform(get("/api/admin/bitacora").param("accion", "cancelar_reserva").header("Authorization", bearer(rosa)))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mvc.perform(get("/api/admin/bitacora")
                        .param("desde", ahora.toLocalDate().minusDays(2).toString())
                        .param("hasta", ahora.toLocalDate().toString())
                        .header("Authorization", bearer(rosa)))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void rangoDeFechasInvertidoResponde400() throws Exception {
        mvc.perform(get("/api/admin/bitacora").param("desde", "2026-11-20").param("hasta", "2026-11-01")
                        .header("Authorization", bearer(rosa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
    }

    @Test
    void fechaMalEscritaResponde400() throws Exception {
        mvc.perform(get("/api/admin/bitacora").param("desde", "20-11-2026").header("Authorization", bearer(rosa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    @Test
    void opcionesDevuelveServiciosYAcciones() throws Exception {
        consumidor.alRecibirAccion(evento("pagos-service", "rhuaman", "VALIDAR_PAGO", Tiempo.ahora()));
        consumidor.alRecibirAccion(evento("catalogo-service", "rhuaman", "EDITAR_CANCHA", Tiempo.ahora()));

        mvc.perform(get("/api/admin/bitacora/opciones").header("Authorization", bearer(rosa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios", hasItems("catalogo-service", "pagos-service")))
                .andExpect(jsonPath("$.acciones", hasItems("EDITAR_CANCHA", "VALIDAR_PAGO")));
    }

    @Test
    void recepcionNoVeLaBitacora() throws Exception {
        Administrador marco = crearUsuario("mtorres", Rol.RECEPCION);
        mvc.perform(get("/api/admin/bitacora").header("Authorization", bearer(marco)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }
}
