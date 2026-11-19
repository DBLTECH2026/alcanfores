package pe.alcanfores.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.alcanfores.reservas.modelo.Turno;
import pe.alcanfores.reservas.servicio.ExpiracionServicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v2 · E: «Mis reservas» del cliente con cuenta (por el DNI del token). */
class MisReservasTest extends PruebaIntegracion {

    @Autowired
    private ExpiracionServicio expiracion;

    @Test
    void incluyeLasReservasHechasAntesDeCrearLaCuentaYSoloLasDeSuDni() throws Exception {
        // Reservas sin cuenta (antes de registrarse): dos del mismo DNI y una de otro.
        String vieja = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").path("codigoReserva").asText();
        String otra = reservarWeb(turno(2, HOY.plusDays(3), 9).getId(), "70123321").path("codigoReserva").asText();
        reservarWeb(turno(3, HOY.plusDays(1), 19).getId(), "70999999");
        mvc.perform(post("/api/reservas/" + otra + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"70123321\",\"motivo\":\"Cambiamos de fecha el partido\"}")).andExpect(status().isOk());

        String cuenta = tokenCliente("70123321");
        mvc.perform(get("/api/cliente/reservas").header("Authorization", cuenta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.tamanio").value(10))
                .andExpect(jsonPath("$.contenido[0].codigoReserva").value(otra))
                .andExpect(jsonPath("$.contenido[0].cancelacion.canceladoPor").value("CLIENTE"))
                .andExpect(jsonPath("$.contenido[0].cancelacion.motivo").value("Cambiamos de fecha el partido"))
                .andExpect(jsonPath("$.contenido[1].codigoReserva").value(vieja))
                .andExpect(jsonPath("$.contenido[1].cancelacion").doesNotExist())
                .andExpect(jsonPath("$.contenido[1].puedeCancelar").value(true))
                .andExpect(jsonPath("$.contenido[1].puedeReenviarComprobante").value(true))
                .andExpect(jsonPath("$.contenido[1].resenada").value(false))
                .andExpect(jsonPath("$.contenido[1].horarioTexto").value("7:00–8:00 p. m."))
                .andExpect(jsonPath("$.contenido[1].cancha.id").value(1));
        mvc.perform(get("/api/cliente/reservas").param("estado", "cancelada").param("size", "1").header("Authorization", cuenta))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].estado").value("CANCELADA"));
        mvc.perform(get("/api/cliente/reservas").param("estado", "VENCIDA").header("Authorization", cuenta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        mvc.perform(get("/api/cliente/reservas/" + vieja.toLowerCase()).header("Authorization", cuenta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoReserva").value(vieja))
                .andExpect(jsonPath("$.linea[0].evento").value("REGISTRADA"));
        mvc.perform(get("/api/cliente/reservas/" + vieja).header("Authorization", tokenCliente("70999999")))
                .andExpect(status().isNotFound());
        // Ninguna de estas reservas es reseñable todavía: no se consulta a catálogo.
        verify(catalogo, never()).tieneResena(anyString());
    }

    @Test
    void laListaDistingueLaQueAnuloElSistemaPorVencerElPlazo() throws Exception {
        String vencida = reservarWeb(turno(1, HOY.plusDays(1), 19).getId(), "70123321").path("codigoReserva").asText();
        fijarReloj(AHORA.plusMinutes(21));
        assertThat(expiracion.expirarVencidas()).isEqualTo(1);

        mvc.perform(get("/api/cliente/reservas").param("estado", "cancelada").header("Authorization", tokenCliente("70123321")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].codigoReserva").value(vencida))
                .andExpect(jsonPath("$.contenido[0].estado").value("CANCELADA"))
                .andExpect(jsonPath("$.contenido[0].cancelacion.canceladoPor").value("SISTEMA"))
                .andExpect(jsonPath("$.contenido[0].cancelacion.motivo").value("Venció el plazo para pagar"));
    }

    @Test
    void unaReservaJugadaYaCalificadaSaleResenada() throws Exception {
        String codigo = reservarWeb(turno(1, HOY, 14).getId(), "70123321").path("codigoReserva").asText();
        jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA', estado_pago = 'ADELANTO_PAGADO' WHERE codigo_reserva = ?", codigo);
        fijarReloj(AHORA.plusHours(5));
        when(catalogo.tieneResena(codigo)).thenReturn(true);
        mvc.perform(get("/api/cliente/reservas").header("Authorization", tokenCliente("70123321")))
                .andExpect(jsonPath("$.contenido[0].resenada").value(true))
                .andExpect(jsonPath("$.contenido[0].puedeResenar").value(false));
        mvc.perform(get("/api/cliente/reservas/" + codigo).header("Authorization", tokenCliente("70123321")))
                .andExpect(jsonPath("$.resenada").value(true));
        when(catalogo.tieneResena(codigo)).thenReturn(false);
        mvc.perform(get("/api/cliente/reservas/" + codigo).header("Authorization", tokenCliente("70123321")))
                .andExpect(jsonPath("$.resenada").value(false))
                .andExpect(jsonPath("$.puedeResenar").value(true));
    }

    @Test
    void conTokenDeClienteSeReservaConElDniDeLaCuenta() throws Exception {
        Turno t = turno(2, HOY.plusDays(1), 20);
        mvc.perform(post("/api/reservas").header("Authorization", tokenCliente("70123321"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoReserva(t.getId(), "70999999")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.dniEnmascarado").value("7****321"));
        assertThat(jdbc.queryForList("SELECT c.dni FROM reserva r JOIN cliente c ON c.id_cliente = r.id_cliente", String.class))
                .containsExactly("70123321");
    }

    @Test
    void soloElRolClienteEntraYNoPuedeUsarElPanel() throws Exception {
        mvc.perform(get("/api/cliente/reservas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cliente/reservas").header("Authorization", token("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reservas").header("Authorization", tokenCliente("70123321"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/turnos").param("fecha", HOY.toString()).header("Authorization", tokenCliente("70123321")))
                .andExpect(status().isForbidden());
    }
}
